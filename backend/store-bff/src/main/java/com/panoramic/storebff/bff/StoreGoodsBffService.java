package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.goods.api.GoodsCenterClient;
import com.panoramic.contract.goods.vo.BrandVO;
import com.panoramic.contract.goods.vo.CategoryTreeVO;
import com.panoramic.contract.goods.vo.SpuBySkuCodeVO;
import com.panoramic.contract.goods.vo.SpuDetailVO;
import com.panoramic.common.security.LoginUser;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuDetailQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.storebff.vo.StoreGoodsSpuDetailBffVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 店铺端 BFF · 店铺在售商品编排。
 * <p>只做页面编排与聚合，不持有/复制 store 域与 goods-center 的任何实体与表：
 * 商品数据经 {@link StoreClient} 调 store 域（作用域 storeId 由本层从登录态取、无条件写进域入参 DTO，
 * 收敛在本层，见 cross-cutting 第 22 条），
 * 分类/品牌下拉与「按 SKU 编码反查中台模板」经 {@link GoodsCenterClient} 调 goods-center。</p>
 * <p><b>审核门禁（R9）</b>：{@code /goods/**} 全部接口（含读接口）先经
 * {@link #assertShopApprovedAndGetStoreId()} 校验「我的店铺」{@code status == 2}，
 * 未通过一律拒绝——域内不做该判断，门禁是端 BFF 的职责。</p>
 * <p><b>版本同步（R10）</b>：详情返回时若关联了中台 SPU，则比对中台当前版本戳与落库的
 * {@code center_version}，不一致置 {@code centerOutdated=true} 并附中台快照 {@code centerSpu}，
 * 由前端给「同步」按钮（覆盖 / 不覆盖由店主决定，不阻断保存）；中台已删或不可达时置
 * {@code centerMissing=true} 而不报错。</p>
 * <p><b>分类全路径（2026-09-12）</b>：列表/详情的 {@code categoryPath} 由本层<b>读时解析</b>——
 * 域不持分类表，故按页内去重后的 {@code categoryId} 批量调 goods-center 换路径（一次调用，非 N+1）。
 * 解析走独立 try/catch 降级：中台不可用时仅告警、路径留空，前端回退落库快照 {@code categoryName}，
 * 绝不让整个列表/详情失败。</p>
 * <p>下游异常处理：业务异常（400 参数/业务、403 权限、404 不存在）沿 cause 链剥出后原样透传，
 * 其余（熔断/连接/序列化等）降级为友好提示，避免拖垮调用方。该逻辑已抽到 common 的
 * {@link BffFeignCall}，本类只传降级文案。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreGoodsBffService {

    /** store 域熔断/连接异常降级提示 */
    private static final String STORE_DEGRADE_MSG = "店铺服务暂不可用，请稍后重试";
    /** goods-center 熔断/连接异常降级提示 */
    private static final String GOODS_DEGRADE_MSG = "商品服务暂不可用，请稍后重试";
    /**
     * 店铺审核状态「已通过」（store 域 store_shop.status 的取值契约：0草稿/1待审核/2已通过/3已驳回）。
     * 域侧状态机归 store 域，本层只读来判断门禁。
     */
    private static final int SHOP_STATUS_APPROVED = 2;

    private final StoreClient storeClient;
    private final GoodsCenterClient goodsCenterClient;

    // ---- 商品（数据在 store 域，经 owner 接口）----

    /**
     * 我的商品分页（仅当前店主名下）：域分页 + 分类全路径读时解析（降级不阻断）。
     * <p>作用域由本层从登录态写进域入参 DTO 并<b>无条件覆盖</b>（页面入参复用同一份 DTO，
     * 页面不提供该字段；cross-cutting 第 22 条）。</p>
     */
    public PageResult<StoreGoodsSpuPageItemVO> page(StoreGoodsSpuPageQueryDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        PageResult<StoreGoodsSpuPageItemVO> result = callStore(() -> storeClient.pageStoreGoods(dto));
        fillCategoryPaths(result.getRecords());
        return result;
    }

    /**
     * 我的商品详情：域详情 + 中台关联版本比对（R10）+ 分类全路径（降级不阻断）。
     * <p>作用域写进 {@link StoreGoodsSpuDetailQueryDTO}（域侧是「传了就按本店筛」）；
     * 出参<b>逐字段手工映射</b>为商户端页面模型，不整份转发域出参（管理端超集，
     * 含 {@code lockUser} 等商户端不下发的字段），见 {@link #toBffVO}。</p>
     */
    public StoreGoodsSpuDetailBffVO detail(Long id) {
        Long storeId = assertShopApprovedAndGetStoreId();
        StoreGoodsSpuDetailQueryDTO query = new StoreGoodsSpuDetailQueryDTO();
        query.setStoreId(storeId);
        StoreGoodsSpuPlatformDetailVO domain = callStore(() -> storeClient.storeGoodsDetail(id, query));

        StoreGoodsSpuDetailBffVO vo = toBffVO(domain);
        vo.setCategoryPath(resolveCategoryPath(domain.getCategoryId()));
        fillCenterLink(vo, domain);
        return vo;
    }

    /**
     * 新增商品（可一并落 SKU；SPU 与 SKU 均以下架态起步）；作用域由本层无条件覆盖
     */
    public Long save(StoreGoodsSpuSaveDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        return callStore(() -> storeClient.saveStoreGoods(dto));
    }

    /**
     * 修改商品（基础信息 + 规格配置；存在上架 SKU 时规格配置只读由域校验）；作用域由本层无条件覆盖
     */
    public void update(Long id, StoreGoodsSpuUpdateDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        callStore(() -> storeClient.updateStoreGoods(id, dto));
    }

    /**
     * 删除商品（存在上架 SKU 时拒绝；否则级联软删 SKU）。
     * <p>作用域是本层从登录态取的值（域侧按 {@code id + store_id} 双条件删除）。</p>
     */
    public void delete(Long id) {
        Long storeId = assertShopApprovedAndGetStoreId();
        callStore(() -> storeClient.deleteStoreGoods(id, storeId));
    }

    /**
     * SKU 整单替换（未上架可增/改/删；已上架须原样保留）；作用域由本层无条件覆盖
     */
    public void replaceSkus(Long id, StoreGoodsSkuReplaceDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        callStore(() -> storeClient.replaceStoreGoodsSkus(id, dto));
    }

    /**
     * SKU 上下架（反向联动 SPU 上下架由域实现）；作用域由本层无条件覆盖
     */
    public void updateSkuShelf(Long spuId, Long skuId, StoreGoodsSkuShelfDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        callStore(() -> storeClient.updateStoreGoodsSkuShelf(spuId, skuId, dto));
    }

    /**
     * 库存管理分页（仅当前店主名下 SKU）：域分页直出，本层不做聚合。
     * <p>库存两列（总库存 / 预警）由域侧读库存表回填；本层不含任何库存口径计算。
     * 作用域由本层无条件覆盖。</p>
     */
    public PageResult<StoreGoodsStockPageItemVO> pageStock(StoreGoodsStockPageQueryDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        return callStore(() -> storeClient.pageSkuStock(dto));
    }

    /**
     * 改单行 SKU 库存（{@code warnStock} 传 null = 清除预警）；平台锁定期由域内拒绝；作用域由本层无条件覆盖
     */
    public void updateSkuStock(Long skuId, StoreGoodsStockUpdateDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        callStore(() -> storeClient.updateSkuStock(skuId, dto));
    }

    /**
     * 批量设置整批 SKU 的总库存（统一设为同一值，不动预警阈值）；作用域由本层无条件覆盖
     */
    public void batchUpdateSkuStock(StoreGoodsStockBatchUpdateDTO dto) {
        dto.setStoreId(assertShopApprovedAndGetStoreId());
        callStore(() -> storeClient.batchUpdateSkuStock(dto));
    }

    // ---- 中台基础数据（下拉 / 预填，经 goods-center）----

    /**
     * 分类树（商品分类下拉）
     */
    public List<CategoryTreeVO> categoryTree() {
        assertShopApprovedAndGetStoreId();
        return callGoods(goodsCenterClient::categoryTree);
    }

    /**
     * 品牌列表（商品品牌下拉，非必填）
     */
    public List<BrandVO> listBrands() {
        assertShopApprovedAndGetStoreId();
        return callGoods(goodsCenterClient::listBrands);
    }

    /**
     * 按 SKU 编码反查中台标准模板（新增商品时填 SKU_CODE 预填整单）。
     * <p>未命中返回 {@code spu == null}（HTTP 200，非错误）——店主可继续自建、不写关联 id；
     * 命中多条时 {@code matchedSkuCount > 1}，取 SKU id 最小者，由前端提示「编码重复，已取第一条」。</p>
     */
    public SpuBySkuCodeVO centerSpuBySkuCode(String skuCode) {
        assertShopApprovedAndGetStoreId();
        return callGoods(() -> goodsCenterClient.spuDetailBySkuCode(skuCode));
    }

    // ---- 分类全路径读时解析（域不持分类表，路径只能在本层补）----

    /**
     * 批量回填列表项的分类全路径（D4）：只对本页去重后的分类 id 发一次请求，避免 N+1。
     * <p>未命中的行保持 {@code categoryPath == null}，前端回退显示落库快照 {@code categoryName}。</p>
     *
     * @param items 本页列表项（就地回填）
     */
    private void fillCategoryPaths(List<StoreGoodsSpuPageItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> categoryIds = items.stream()
                .map(StoreGoodsSpuPageItemVO::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> paths = fetchCategoryPaths(categoryIds);
        if (paths.isEmpty()) {
            return;
        }
        for (StoreGoodsSpuPageItemVO item : items) {
            item.setCategoryPath(paths.get(item.getCategoryId()));
        }
    }

    /**
     * 解析单条分类全路径（详情用）；未命中返回 null（前端回退快照名）
     *
     * @param categoryId 分类 id
     * @return 如「服饰 / 男装 / T恤」；解析失败或未命中为 null
     */
    private String resolveCategoryPath(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return fetchCategoryPaths(List.of(categoryId)).get(categoryId);
    }

    /**
     * 调 goods-center 批量取分类全路径。
     * <p><b>降级隔离（D9）</b>：中台不可用 / 熔断 / 业务异常一律只告警并返回空 Map——
     * 分类路径是展示增强，缺失不能把整个列表或详情拖失败（前端回退显示快照名）。</p>
     *
     * @param categoryIds 分类 id 集合（已去重）
     * @return id -> 路径；不可得时空 Map
     */
    private Map<Long, String> fetchCategoryPaths(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<Long, String> paths = callGoods(() -> goodsCenterClient.categoryPaths(categoryIds));
            return paths == null ? Collections.emptyMap() : paths;
        } catch (ServiceException e) {
            log.warn("分类全路径解析失败，回退显示快照分类名: categoryIds={}, msg={}", categoryIds, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ---- 门禁与编排辅助 ----

    /**
     * 审核门禁（R9）：校验当前店主的店铺已审核通过（{@code status == 2}），返回 store_id。
     * <p>门禁覆盖 {@code /goods/**} 的读与写；未开店、审核中、已驳回、已通过前一律拒绝。
     * 域侧详情接口「查不到返空、不抛」，故这里判的是 {@code null}（未开店）而非下游 4xx。</p>
     *
     * @return 当前店主账号 id（== store_id，账号店同 ID）
     */
    private Long assertShopApprovedAndGetStoreId() {
        Long storeId = currentStoreId();
        ShopVO shop = callStore(() -> storeClient.getShop(storeId));
        if (shop == null || !Objects.equals(shop.getStatus(), SHOP_STATUS_APPROVED)) {
            throw new ServiceException(403, "店铺未通过审核，暂不可管理商品");
        }
        return storeId;
    }

    /**
     * 域详情 → 商户端页面模型：<b>逐字段手工映射</b>（不用 {@code BeanUtils.copyProperties}）。
     * <p>域出参是管理端超集，整份转发会把不该下发的字段带给商户端页面：
     * <ul>
     *   <li>{@code lockUser}（锁定人）<b>不映射</b>——商户端只展示锁定原因与时间（既有口径）；</li>
     *   <li>{@code storeName} / {@code categoryPath} 也不从域取：前者是跨店视角的字段、后者由本层读时解析。</li>
     * </ul>
     * 新增域字段时的默认动作是<b>不映射</b>——要下发得先在这一行显式写出来。</p>
     *
     * @param src 域详情（非空）
     * @return 商户端页面模型
     */
    private static StoreGoodsSpuDetailBffVO toBffVO(StoreGoodsSpuPlatformDetailVO src) {
        StoreGoodsSpuDetailBffVO vo = new StoreGoodsSpuDetailBffVO();
        vo.setId(src.getId());
        vo.setStoreId(src.getStoreId());
        vo.setName(src.getName());
        vo.setCategoryId(src.getCategoryId());
        vo.setCategoryName(src.getCategoryName());
        vo.setBrandId(src.getBrandId());
        vo.setBrandName(src.getBrandName());
        vo.setMainImage(src.getMainImage());
        vo.setImageList(src.getImageList());
        vo.setDescription(src.getDescription());
        vo.setSpecConfig(src.getSpecConfig());
        vo.setShelfStatus(src.getShelfStatus());
        vo.setLockStatus(src.getLockStatus());
        vo.setLockReason(src.getLockReason());
        vo.setLockTime(src.getLockTime());
        vo.setGoodsSpuId(src.getGoodsSpuId());
        vo.setCenterVersion(src.getCenterVersion());
        vo.setSkus(src.getSkus());
        vo.setCreateTime(src.getCreateTime());
        vo.setUpdateTime(src.getUpdateTime());
        return vo;
    }

    /**
     * 中台关联版本比对（R10）：按 goodsSpuId 取中台当前模板，与落库的 center_version 比对。
     * <p>中台已删 / 不可达时不报错，置 {@code centerMissing=true}；比对不等的判定用
     * {@link Objects#equals}——两端版本均为空时视为一致（未关联或均未记录）。</p>
     *
     * @param vo     页面态详情（写入 centerOutdated/centerMissing/centerSpu）
     * @param domain 域返回的详情
     */
    private void fillCenterLink(StoreGoodsSpuDetailBffVO vo, StoreGoodsSpuPlatformDetailVO domain) {
        if (domain.getGoodsSpuId() == null) {
            // 未关联中台：无需比对，前端不展示同步入口
            vo.setCenterOutdated(false);
            vo.setCenterMissing(false);
            return;
        }
        SpuDetailVO center = fetchCenterSpu(domain.getGoodsSpuId());
        if (center == null) {
            vo.setCenterOutdated(false);
            vo.setCenterMissing(true);
            return;
        }
        vo.setCenterSpu(center);
        vo.setCenterMissing(false);
        vo.setCenterOutdated(!Objects.equals(center.getVersion(), domain.getCenterVersion()));
    }

    /**
     * 取中台模板快照；查不到（已删除）或调用失败均返回 null，由调用方置 centerMissing（不报错）
     *
     * @param goodsSpuId 中台 SPU id
     * @return 中台模板详情；不可得返回 null
     */
    private SpuDetailVO fetchCenterSpu(Long goodsSpuId) {
        try {
            return callGoods(() -> goodsCenterClient.spuDetail(goodsSpuId));
        } catch (ServiceException e) {
            // 中台 detail 查不到时抛的是业务异常「商品不存在」= code 400（域内不用 404），故两者都按「已删除」记 warn
            Integer code = e.getCode();
            if (code != null && (code == 400 || code == 404)) {
                log.warn("关联的中台模板已不存在: goodsSpuId={}, msg={}", goodsSpuId, e.getMessage());
            } else {
                log.error("取中台模板失败，按缺失处理: goodsSpuId={}, msg={}", goodsSpuId, e.getMessage());
            }
            return null;
        }
    }

    /**
     * 取当前登录店主账号 id（== store_id），登录态缺失时拒绝
     */
    private Long currentStoreId() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException("登录已失效，请重新登录");
        }
        return loginUser.getId();
    }

    /**
     * 调 store 域的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callStore(Supplier<RespData<T>> action) {
        return BffFeignCall.call("store", STORE_DEGRADE_MSG, action);
    }

    /**
     * 调 goods-center 的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callGoods(Supplier<RespData<T>> action) {
        return BffFeignCall.call("goods-center", GOODS_DEGRADE_MSG, action);
    }
}
