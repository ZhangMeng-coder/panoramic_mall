package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.goods.api.GoodsCenterClient;
import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.goods.vo.SpuBySkuCodeVO;
import com.panoramic.common.goods.vo.SpuDetailVO;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.store.api.StoreClient;
import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.common.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.common.util.UserContext;
import com.panoramic.storebff.vo.StoreGoodsSpuDetailBffVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 店铺端 BFF · 店铺在售商品编排。
 * <p>只做页面编排与聚合，不持有/复制 store 域与 goods-center 的任何实体与表：
 * 商品数据经 {@link StoreClient} owner 接口调 store 域（storeId 取登录态，归属收敛在本层），
 * 分类/品牌下拉与「按 SKU 编码反查中台模板」经 {@link GoodsCenterClient} 调 goods-center。</p>
 * <p><b>审核门禁（R9）</b>：{@code /goods/**} 全部接口（含读接口）先经
 * {@link #assertShopApprovedAndGetStoreId()} 校验「我的店铺」{@code status == 2}，
 * 未通过一律拒绝——域内不做该判断，门禁是端 BFF 的职责。</p>
 * <p><b>版本同步（R10）</b>：详情返回时若关联了中台 SPU，则比对中台当前版本戳与落库的
 * {@code center_version}，不一致置 {@code centerOutdated=true} 并附中台快照 {@code centerSpu}，
 * 由前端给「同步」按钮（覆盖 / 不覆盖由店主决定，不阻断保存）；中台已删或不可达时置
 * {@code centerMissing=true} 而不报错。</p>
 * <p>下游异常处理：业务异常（400 参数/业务、403 权限）沿 cause 链剥出后原样透传，
 * 其余（熔断/连接/序列化等）降级为友好提示，避免拖垮调用方。</p>
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
     * 我的商品分页（仅当前店主名下）
     */
    public PageResult<StoreGoodsSpuPageItemVO> page(StoreGoodsSpuPageQueryDTO dto) {
        Long storeId = assertShopApprovedAndGetStoreId();
        return callStore(() -> storeClient.pageStoreGoods(storeId, dto));
    }

    /**
     * 我的商品详情：域详情 + 中台关联版本比对（R10）
     */
    public StoreGoodsSpuDetailBffVO detail(Long id) {
        Long storeId = assertShopApprovedAndGetStoreId();
        StoreGoodsSpuDetailVO domain = callStore(() -> storeClient.storeGoodsDetail(id, storeId));

        StoreGoodsSpuDetailBffVO vo = new StoreGoodsSpuDetailBffVO();
        BeanUtils.copyProperties(domain, vo);
        fillCenterLink(vo, domain);
        return vo;
    }

    /**
     * 新增商品（可一并落 SKU；SPU 与 SKU 均以下架态起步）
     */
    public Long save(StoreGoodsSpuSaveDTO dto) {
        Long storeId = assertShopApprovedAndGetStoreId();
        return callStore(() -> storeClient.saveStoreGoods(storeId, dto));
    }

    /**
     * 修改商品（基础信息 + 规格配置；存在上架 SKU 时规格配置只读由域校验）
     */
    public void update(Long id, StoreGoodsSpuUpdateDTO dto) {
        Long storeId = assertShopApprovedAndGetStoreId();
        callStore(() -> {
            storeClient.updateStoreGoods(id, storeId, dto);
            return null;
        });
    }

    /**
     * 删除商品（存在上架 SKU 时拒绝；否则级联软删 SKU）
     */
    public void delete(Long id) {
        Long storeId = assertShopApprovedAndGetStoreId();
        callStore(() -> {
            storeClient.deleteStoreGoods(id, storeId);
            return null;
        });
    }

    /**
     * SKU 整单替换（未上架可增/改/删；已上架须原样保留）
     */
    public void replaceSkus(Long id, StoreGoodsSkuReplaceDTO dto) {
        Long storeId = assertShopApprovedAndGetStoreId();
        callStore(() -> {
            storeClient.replaceStoreGoodsSkus(id, storeId, dto);
            return null;
        });
    }

    /**
     * SKU 上下架（反向联动 SPU 上下架由域实现）
     */
    public void updateSkuShelf(Long spuId, Long skuId, StoreGoodsSkuShelfDTO dto) {
        Long storeId = assertShopApprovedAndGetStoreId();
        callStore(() -> {
            storeClient.updateStoreGoodsSkuShelf(spuId, skuId, storeId, dto);
            return null;
        });
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

    // ---- 门禁与编排辅助 ----

    /**
     * 审核门禁（R9）：校验当前店主的店铺已审核通过（{@code status == 2}），返回 store_id。
     * <p>门禁覆盖 {@code /goods/**} 的读与写；未开店、审核中、已驳回、已通过前一律拒绝。</p>
     *
     * @return 当前店主账号 id（== store_id，账号店同 ID）
     */
    private Long assertShopApprovedAndGetStoreId() {
        Long storeId = currentStoreId();
        ShopVO shop = callStore(() -> storeClient.mineShop(storeId));
        if (shop == null || !Objects.equals(shop.getStatus(), SHOP_STATUS_APPROVED)) {
            throw new ServiceException(403, "店铺未通过审核，暂不可管理商品");
        }
        return storeId;
    }

    /**
     * 中台关联版本比对（R10）：按 goodsSpuId 取中台当前模板，与落库的 center_version 比对。
     * <p>中台已删 / 不可达时不报错，置 {@code centerMissing=true}；比对不等的判定用
     * {@link Objects#equals}——两端版本均为空时视为一致（未关联或均未记录）。</p>
     *
     * @param vo     页面态详情（写入 centerOutdated/centerMissing/centerSpu）
     * @param domain 域返回的详情
     */
    private void fillCenterLink(StoreGoodsSpuDetailBffVO vo, StoreGoodsSpuDetailVO domain) {
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
     * 调 store 域的统一编排执行
     */
    private <T> T callStore(Supplier<T> action) {
        return call("store", STORE_DEGRADE_MSG, action);
    }

    /**
     * 调 goods-center 的统一编排执行
     */
    private <T> T callGoods(Supplier<T> action) {
        return call("goods-center", GOODS_DEGRADE_MSG, action);
    }

    /**
     * 统一编排执行：业务异常（400 参数/业务、403 权限）透传，其余（熔断/连接/序列化等）降级为友好提示。
     * <p>⚠ Feign + 熔断会把下游抛出的业务异常包装成 {@code NoFallbackAvailableException}/
     * {@code ExecutionException}/{@code CompletionException} 等再抛出，因此须沿 cause 链定位原始
     * {@link ServiceException}；否则 400/403 会被误当成连接故障降级为 500「服务暂不可用」。</p>
     *
     * @param downstream 下游服务名（日志用）
     * @param degradeMsg 降级提示文案
     * @param action     实际调用
     */
    private <T> T call(String downstream, String degradeMsg, Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            // 沿 cause 链找下游业务异常，剥开熔断/异步包装层
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof ServiceException se) {
                    Integer code = se.getCode();
                    if (code != null && (code == 400 || code == 403 || code == 404)) {
                        throw se; // 参数/业务(400)、权限(403)、不存在(404)：原样透传，由统一异常处理还原给页面
                    }
                    log.warn("{} 调用异常，降级处理: code={}, msg={}", downstream, se.getCode(), se.getMessage());
                    throw new ServiceException(500, degradeMsg);
                }
            }
            // 非业务异常：熔断开启 / 连接失败 / 序列化等 → 降级为友好提示
            log.error("{} 调用失败，降级处理", downstream, e);
            throw new ServiceException(500, degradeMsg);
        }
    }
}
