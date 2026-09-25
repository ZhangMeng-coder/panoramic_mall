package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.store.dto.ShopAuditDTO;
import com.panoramic.contract.store.dto.ShopPageQueryDTO;
import com.panoramic.contract.store.dto.ShopSaveDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopOptionVO;
import com.panoramic.contract.store.vo.ShopVO;
import com.panoramic.common.util.UserContext;
import com.panoramic.store.entity.StoreShop;
import com.panoramic.store.mapper.StoreShopMapper;
import com.panoramic.store.service.StoreShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 店铺服务实现（store 域下沉纯域）。
 * <p>「账号店同 ID」（一人一店）：店铺主键 id == 店主账号 id，故店铺详情就是对某一行的直查
 * （{@link #getShop}）——店主侧查自己账号 id 那份、管理端跨店查同一行，<b>不按端分侧</b>。
 * <b>域内不做权限判断</b>：写方法的 storeId 取自入参 DTO 并由端 BFF 从登录态覆盖，
 * 域内只按方法语义执行。查不到一律返空、不抛（「未开店」是正常态）。</p>
 * <p>身份头只用于两处：MyBatis-Plus 审计字段自动填充（{@code UserType:UserId}）与
 * {@code audit_by} 直取 X-User-Id 留痕（D6，不与平台账号联查）。缺头则留空，不回 401。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreShopServiceImpl extends ServiceImpl<StoreShopMapper, StoreShop> implements StoreShopService {

    @Override
    public ShopVO getShop(Long id) {
        // 查不到 = null（HTTP 200 空 body → Feign null）：未开店 / 店铺不可见由调用方各自重判
        return toVO(getById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long storeId, ShopSaveDTO dto) {
        StoreShop shop = getById(storeId);
        boolean isNew = shop == null;
        if (isNew) {
            // 第一次开店：建 id=store_id(账号id) 的店行
            shop = new StoreShop();
            shop.setId(storeId);
        } else {
            // 状态机守卫：审核中锁定、已通过只读
            if (shop.getStatus() != null && shop.getStatus() == StoreShop.STATUS_PENDING) {
                throw new ServiceException("店铺审核中，暂不可修改");
            }
            if (shop.getStatus() != null && shop.getStatus() == StoreShop.STATUS_APPROVED) {
                throw new ServiceException("店铺已审核通过，信息已锁定");
            }
        }
        applyShopFields(shop, dto);
        shop.setStatus(StoreShop.STATUS_DRAFT);
        if (isNew) {
            save(shop);
        } else {
            // 已驳回(3)重新编辑 → 回到草稿，清空此前的提交/审核留痕，须重新提交
            clearAuditTrace(shop);
            resetAuditTraceColumns(storeId);
            updateById(shop);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long storeId, ShopSaveDTO dto) {
        StoreShop shop = getById(storeId);
        boolean isNew = shop == null;
        if (!isNew) {
            if (shop.getStatus() != null && shop.getStatus() == StoreShop.STATUS_PENDING) {
                throw new ServiceException("店铺审核中，请勿重复提交");
            }
            if (shop.getStatus() != null && shop.getStatus() == StoreShop.STATUS_APPROVED) {
                throw new ServiceException("店铺已审核通过，无需再次提交");
            }
        }
        // 提交即按「完整资质」校验
        assertSubmitComplete(dto);
        if (isNew) {
            shop = new StoreShop();
            shop.setId(storeId);
        }
        applyShopFields(shop, dto);
        // 先清上一次驳回/提交留痕（含 submit_time），再登记本次提交时间与待审核状态，
        // 避免 clearAuditTrace 把刚写的 submit_time 置空
        clearAuditTrace(shop);
        shop.setStatus(StoreShop.STATUS_PENDING);
        shop.setSubmitTime(LocalDateTime.now());
        if (isNew) {
            save(shop);
        } else {
            // 重新提交：显式把库里上一轮审核留痕列写 NULL（updateById 默认跳过 null 字段，清不干净）
            resetAuditTraceColumns(storeId);
            updateById(shop);
        }
    }

    @Override
    public PageResult<ShopVO> adminPage(ShopPageQueryDTO dto) {
        Page<StoreShop> shopPage = dto.toPage(StoreShop.class);
        IPage<StoreShop> result = page(shopPage,
                Wrappers.<StoreShop>lambdaQuery()
                        .eq(dto.getStatus() != null, StoreShop::getStatus, dto.getStatus())
                        .and(StringUtils.hasText(dto.getKeyword()), w -> w
                                .like(StoreShop::getShopName, dto.getKeyword())
                                .or().like(StoreShop::getContactName, dto.getKeyword())
                                .or().like(StoreShop::getLicenseName, dto.getKeyword())
                                .or().like(StoreShop::getLicenseNo, dto.getKeyword()))
                        .orderByDesc(StoreShop::getSubmitTime)
                        .orderByDesc(StoreShop::getId));
        return new PageResult<>(result.getTotal(), toVOList(result.getRecords()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminAudit(Long id, ShopAuditDTO dto) {
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        if (!approved && !StringUtils.hasText(dto.getAuditRemark())) {
            throw new ServiceException("驳回时必须填写驳回原因");
        }

        // 以「待审核(1)」为条件做条件更新：他人已审核或店铺状态变更时更新 0 行 → 拒绝（防重复/并发审核）
        StoreShop patch = new StoreShop();
        patch.setStatus(approved ? StoreShop.STATUS_APPROVED : StoreShop.STATUS_REJECTED);
        // audit_by 直取 X-User-Id 仅留痕（D6：不与平台账号联查，缺头则为 null）
        patch.setAuditBy(UserContext.getUserId());
        patch.setAuditTime(LocalDateTime.now());
        if (!approved) {
            patch.setAuditRemark(dto.getAuditRemark().trim());
        }
        boolean updated = update(patch,
                Wrappers.<StoreShop>lambdaUpdate()
                        .eq(StoreShop::getId, id)
                        .eq(StoreShop::getStatus, StoreShop.STATUS_PENDING));
        if (!updated) {
            throw new ServiceException("店铺不存在或已被审核，请刷新后重试");
        }
    }

    @Override
    public Map<Long, String> nameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return listByIds(ids).stream()
                .collect(Collectors.toMap(StoreShop::getId,
                        shop -> shop.getShopName() == null ? "" : shop.getShopName(), (a, b) -> a));
    }

    @Override
    public Map<Long, Integer> statusMap(Collection<Long> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 只读批量回填：查不到的 id 不进结果（调用方得 null），不补 0、不抛异常
        Map<Long, Integer> map = new HashMap<>();
        for (StoreShop shop : listByIds(storeIds)) {
            map.put(shop.getId(), shop.getStatus());
        }
        return map;
    }

    @Override
    public List<Long> idListByStatus(Integer status) {
        return list(Wrappers.<StoreShop>lambdaQuery()
                .select(StoreShop::getId)
                .eq(StoreShop::getStatus, status))
                .stream().map(StoreShop::getId).collect(Collectors.toList());
    }

    @Override
    public List<ShopOptionVO> options() {
        return list(Wrappers.<StoreShop>lambdaQuery().orderByAsc(StoreShop::getId)).stream()
                .map(shop -> {
                    ShopOptionVO vo = new ShopOptionVO();
                    vo.setId(shop.getId());
                    vo.setShopName(shop.getShopName());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ---- 评价协作（跨实体只走 owner service：评价服务调本方法，本域不把店铺 Mapper 递出去）----

    @Override
    public void updateScore(Long storeId, BigDecimal score) {
        // 店铺评分类推导量的唯一写入口（调用方是评价服务，由它重算本店全部评价的算术平均后传入）。
        // ⚠ 必须显式 set：updateById 跳过 null 列，「无评价 → 清回 NULL」会静默不落库。
        // 店铺自身的写路径（saveDraft / submit / adminAudit）都不得显式设置该列。
        lambdaUpdate()
                .set(StoreShop::getScore, score)
                .eq(StoreShop::getId, storeId)
                .update();
    }

    // ---- 店铺字段/留痕辅助（状态机与审核留痕清理逻辑从旧实现平移，去掉账号回填）----

    /**
     * 复制入参到店铺（同名业务字段覆盖）
     */
    private void applyShopFields(StoreShop shop, ShopSaveDTO dto) {
        BeanUtils.copyProperties(dto, shop);
    }

    /**
     * 清空提交/审核留痕（店主保存草稿、重新提交时）
     */
    private void clearAuditTrace(StoreShop shop) {
        shop.setSubmitTime(null);
        shop.setAuditBy(null);
        shop.setAuditTime(null);
        shop.setAuditRemark(null);
    }

    /**
     * 把店铺审核留痕列显式写 NULL（编辑驳回回草稿、驳回后重新提交时兜底清库）。
     * <p>MyBatis-Plus 默认 update 策略为 NOT_NULL：实体字段为 null 时 {@code updateById} 会跳过该列，
     * 因此仅靠 {@link #clearAuditTrace} 置空实体字段，库里上一轮的 submit_time/audit_by/audit_time/
     * audit_remark 并不会被清掉，需本条 UPDATE 把这些列显式写 NULL。</p>
     */
    private void resetAuditTraceColumns(Long shopId) {
        update(null, Wrappers.<StoreShop>lambdaUpdate()
                .eq(StoreShop::getId, shopId)
                .set(StoreShop::getSubmitTime, null)
                .set(StoreShop::getAuditBy, null)
                .set(StoreShop::getAuditTime, null)
                .set(StoreShop::getAuditRemark, null));
    }

    /**
     * 提交审核时的完整资质校验（保存草稿不校验，提交必填）
     */
    private void assertSubmitComplete(ShopSaveDTO dto) {
        List<String> missing = new ArrayList<>();
        checkRequired(missing, "联系人", dto.getContactName());
        checkRequired(missing, "联系人电话", dto.getContactPhone());
        checkRequired(missing, "所在地区", dto.getRegion());
        checkRequired(missing, "详细地址", dto.getAddress());
        checkRequired(missing, "营业执照企业名称", dto.getLicenseName());
        checkRequired(missing, "统一社会信用代码", dto.getLicenseNo());
        checkRequired(missing, "营业执照照片", dto.getLicenseImg());
        if (!missing.isEmpty()) {
            throw new ServiceException("提交前请补全：" + String.join("、", missing));
        }
    }

    private void checkRequired(List<String> missing, String label, String value) {
        if (!StringUtils.hasText(value)) {
            missing.add(label);
        }
    }

    private List<ShopVO> toVOList(List<StoreShop> shops) {
        return shops.stream().map(this::toVO).collect(Collectors.toList());
    }

    private ShopVO toVO(StoreShop shop) {
        if (shop == null) {
            return null;
        }
        ShopVO vo = new ShopVO();
        BeanUtils.copyProperties(shop, vo);
        return vo;
    }
}
