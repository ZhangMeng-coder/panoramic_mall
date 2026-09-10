package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.store.dto.ShopAuditDTO;
import com.panoramic.common.store.dto.ShopPageQueryDTO;
import com.panoramic.common.store.dto.ShopSaveDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 店铺服务实现（store 域下沉纯域）。
 * <p>「账号店同 ID」（一人一店）：店铺主键 id == 店主账号 id，owner 方法以 store_id(=账号 id) 直查/直写
 * 「id==store_id 的店」；platform 方法全量。方法内按 {@code X-User-Type}（admin/store）分流：
 * owner 校验 userType==store 且 store_id==X-User-Id（防越权），platform 校验 userType==admin 兜底。
 * 已不再回填店主登录账号（D6：admin 不读账号，域内无 store_user 表）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreShopServiceImpl extends ServiceImpl<StoreShopMapper, StoreShop> implements StoreShopService {

    // ---- owner（store-bff，X-User-Type=store，仅作用于 id==store_id 的店）----

    @Override
    public ShopVO mine(Long storeId) {
        assertOwner(storeId);
        // 账号店同 ID：id==store_id；未开店 getById 返回 null（契约：HTTP 200 空 body → Feign null）
        return toVO(getById(storeId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(Long storeId, ShopSaveDTO dto) {
        assertOwner(storeId);
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
        assertOwner(storeId);
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

    // ---- platform（admin，X-User-Type=admin，全量）----

    @Override
    public PageResult<ShopVO> adminPage(ShopPageQueryDTO dto) {
        requirePlatformAdmin();
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
    public ShopVO adminDetail(Long id) {
        requirePlatformAdmin();
        return toVO(getByIdOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminAudit(Long id, ShopAuditDTO dto) {
        Long operatorId = requirePlatformAdmin();
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        if (!approved && !StringUtils.hasText(dto.getAuditRemark())) {
            throw new ServiceException("驳回时必须填写驳回原因");
        }

        // 以「待审核(1)」为条件做条件更新：他人已审核或店铺状态变更时更新 0 行 → 拒绝（防重复/并发审核）
        StoreShop patch = new StoreShop();
        patch.setStatus(approved ? StoreShop.STATUS_APPROVED : StoreShop.STATUS_REJECTED);
        patch.setAuditBy(operatorId);
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

    // ---- 身份/归属适配（D5）----

    /**
     * 校验当前为店主（X-User-Type=store）且 store_id == X-User-Id（防越权）。
     * 账号店同 ID：owner 操作对象恒为「id==store_id 的店」。
     */
    private void assertOwner(Long storeId) {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || !LoginUser.USER_TYPE_STORE.equals(loginUser.getUserType())
                || loginUser.getId() == null) {
            throw new ServiceException("仅店主可执行该操作");
        }
        if (storeId == null || !storeId.equals(loginUser.getId())) {
            throw new ServiceException("无权操作该店铺");
        }
    }

    /**
     * 取当前登录平台管理员（校验 X-User-Type=admin），返回其账号ID（写入 audit_by）
     */
    private Long requirePlatformAdmin() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || !LoginUser.USER_TYPE_ADMIN.equals(loginUser.getUserType())
                || loginUser.getId() == null) {
            throw new ServiceException("仅平台管理员可执行该操作");
        }
        return loginUser.getId();
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

    /**
     * 根据 ID 查询店铺（不存在抛出业务异常）
     */
    private StoreShop getByIdOrThrow(Long id) {
        StoreShop shop = getById(id);
        if (shop == null) {
            throw new ServiceException("店铺不存在");
        }
        return shop;
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
