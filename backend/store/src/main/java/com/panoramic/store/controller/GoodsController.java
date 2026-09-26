package com.panoramic.store.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.dto.StoreGoodsLockDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuDetailQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.dto.StoreScopeGroup;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import com.panoramic.store.service.StoreGoodsSpuService;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 店铺在售商品内部领域接口（store 域下沉纯域）。
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/store/goods/**}）调用，不对页面暴露公网路由；
 * 方法一律返回 {@code RespData<T>}——业务失败（{@code code=400/403/404}）也走 <b>HTTP 200 + {code,msg}</b>，
 * 只有兜底异常才由 common 的 {@code GlobalExceptionHandler} 返 HTTP 500。
 * 权限判定与店铺审核门禁（{@code status == 2}，R9）已收敛在端 BFF，
 * 本接口只负责执行；写操作审计 user_id 由 {@code StoreUserIdentityFilter} 从 X-User-Id 直取填充。</p>
 * <p><b>接口按能力通用、不按端分侧</b>（cross-cutting 第 22/23 条）：同一能力只有一条路径，
 * 路径段里不出现端别子段；数据作用域（{@code storeId}）<b>不进路径段</b>，只是入参 DTO 的一项——
 * 有作用域维度的能力（商品 CRUD / SKU / 库存）必填，跨店通用能力（分页 / 聚合 / 详情 / 批量详情 /
 * 锁定解锁）不传就是不限定。域内只做「传了就按它筛」，不判身份、不读 {@code X-User-Type} 判权。</p>
 * <p><b>作用域必填的写法</b>：DTO 上的 {@code storeId} 只在域入口必填，
 * 故域侧用 {@code @Validated({Default.class, StoreScopeGroup.class})}（页面入口那些 DTO 是同一份类型，
 * 只跑默认组，见 {@link StoreScopeGroup}）。缺 {@code storeId} 的写入请求应得 <b>{@code code=400}</b>，而不是 NPE。</p>
 */
@RestController
@RequestMapping("/internal/store/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final StoreGoodsSpuService storeGoodsSpuService;

    // ---- 有作用域维度（作用域 storeId 必填，无全量视角；管理端跨店走本类尾部的跨店通用接口）----

    /**
     * 商品分页（仅 {@code dto.storeId} 名下的商品）
     */
    @GetMapping("/spu/page")
    public RespData<PageResult<StoreGoodsSpuPageItemVO>> page(
            @Validated({Default.class, StoreScopeGroup.class}) StoreGoodsSpuPageQueryDTO dto) {
        return RespData.success(storeGoodsSpuService.page(dto.getStoreId(), dto));
    }

    /**
     * 商品详情（含 SKU 列表与锁定信息）。
     * <p>作用域 {@code query.storeId} <b>可空</b>：传了就按它筛（店主侧传自己的店，不属本店与不存在
     * 同样报 400、不泄露存在性），没传就是不限定（管理端 / C 端跨店详情）。
     * 注：{@code /spu/page} 为字面量路径，优先于 {@code /spu/{id}} 模板匹配。</p>
     */
    @GetMapping("/spu/{id}")
    public RespData<StoreGoodsSpuPlatformDetailVO> detail(@PathVariable("id") Long id,
                                               StoreGoodsSpuDetailQueryDTO query) {
        return RespData.success(storeGoodsSpuService.detail(id, query.getStoreId()));
    }

    /**
     * 新增商品（可一并落 SKU；SPU 与 SKU 均以下架态起步）
     */
    @PostMapping("/spu")
    public RespData<Long> save(@Validated({Default.class, StoreScopeGroup.class}) @RequestBody StoreGoodsSpuSaveDTO dto) {
        return RespData.success(storeGoodsSpuService.save(dto.getStoreId(), dto));
    }

    /**
     * 修改商品（基础信息 + 规格属性配置；存在上架 SKU 时规格配置只读）
     */
    @PutMapping("/spu/{id}")
    public RespData<Void> update(@PathVariable("id") Long id,
                       @Validated({Default.class, StoreScopeGroup.class}) @RequestBody StoreGoodsSpuUpdateDTO dto) {
        storeGoodsSpuService.update(dto.getStoreId(), id, dto);
        return RespData.success();
    }

    /**
     * 删除商品（存在上架 SKU 时拒绝；否则软删并级联软删其下全部 SKU）。
     * <p>⚠ 本方法的作用域刻意仍是<b>裸参</b>（cross-cutting 第 23 条的唯一例外）：路径变量之外只有
     * {@code storeId} 一个参数，不为它单造一份一次性 DTO。代价是要守住「域内按 {@code id + store_id}
     * 双条件删除」这条不变量——见 {@code StoreGoodsSpuServiceImpl#delete}。</p>
     */
    @DeleteMapping("/spu/{id}")
    public RespData<Void> delete(@PathVariable("id") Long id,
                       @RequestParam("storeId") Long storeId) {
        storeGoodsSpuService.delete(id, storeId);
        return RespData.success();
    }

    /**
     * SKU 整单替换（未上架可增/改/删；已上架须原样保留且不得缺失）
     */
    @PutMapping("/spu/{id}/skus")
    public RespData<Void> replaceSkus(@PathVariable("id") Long id,
                            @Validated({Default.class, StoreScopeGroup.class}) @RequestBody StoreGoodsSkuReplaceDTO dto) {
        storeGoodsSpuService.replaceSkus(dto.getStoreId(), id, dto);
        return RespData.success();
    }

    /**
     * SKU 上下架（不受 SPU 状态限制，并反向联动 SPU 上下架）
     */
    @PutMapping("/spu/{spuId}/skus/{skuId}/shelf")
    public RespData<Void> updateSkuShelf(@PathVariable("spuId") Long spuId,
                               @PathVariable("skuId") Long skuId,
                               @Validated({Default.class, StoreScopeGroup.class}) @RequestBody StoreGoodsSkuShelfDTO dto) {
        storeGoodsSpuService.updateSkuShelf(dto.getStoreId(), spuId, skuId, dto.getShelfStatus());
        return RespData.success();
    }

    // ---- 库存（有作用域维度，storeId 必填；库存独立成表，读写只碰库存表）----

    /**
     * SKU 库存分页（仅 {@code dto.storeId} 名下商品的 SKU；按 SKU 平铺一行一条，
     * 支持商品名 / SKU 编码关键字、上下架筛选、仅看低库存）
     */
    @GetMapping("/stock/page")
    public RespData<PageResult<StoreGoodsStockPageItemVO>> pageSkuStock(
            @Validated({Default.class, StoreScopeGroup.class}) StoreGoodsStockPageQueryDTO dto) {
        return RespData.success(storeGoodsSpuService.pageStock(dto.getStoreId(), dto));
    }

    /**
     * 改单行 SKU 库存（仅 {@code dto.storeId} 名下）；{@code warnStock} 传 null = 清除预警。平台锁定期只读。
     */
    @PutMapping("/stock/{skuId}")
    public RespData<Void> updateSkuStock(@PathVariable("skuId") Long skuId,
                               @Validated({Default.class, StoreScopeGroup.class}) @RequestBody StoreGoodsStockUpdateDTO dto) {
        storeGoodsSpuService.updateSkuStock(dto.getStoreId(), skuId, dto);
        return RespData.success();
    }

    /**
     * 批量设置整批 SKU 的总库存（仅 {@code dto.storeId} 名下，单条 IN 更新）；平台锁定期只读。
     */
    @PutMapping("/stock/batch")
    public RespData<Void> batchUpdateSkuStock(
            @Validated({Default.class, StoreScopeGroup.class}) @RequestBody StoreGoodsStockBatchUpdateDTO dto) {
        storeGoodsSpuService.batchUpdateSkuStock(dto.getStoreId(), dto);
        return RespData.success();
    }

    // ---- 跨店通用（无作用域锚点，限定条件全由调用方自设）----

    /**
     * 商品分页（<b>跨店通用</b>：不带 storeId 锚点，调用方自设限定条件；
     * 分类与品牌为多值，categoryIds 由端 BFF 用分类树展开后传入）。
     * <p>admin BFF「店铺商品管理」不设限定条件（全量）；mall-bff C 端浏览固定传
     * shopStatus=2 + shelfStatus=1 + lockStatus=0。域侧不含 C 端隐含约束。</p>
     * <p>用 POST + body 而非 query 参数：categoryIds/brandIds 是集合，走 body 规避 @SpringQueryMap 的集合序列化问题。</p>
     */
    @PostMapping("/cross-shop/spu/page")
    public RespData<PageResult<StoreGoodsSpuCrossShopPageItemVO>> crossShopPage(
            @Validated @RequestBody StoreGoodsSpuCrossShopPageQueryDTO dto) {
        return RespData.success(storeGoodsSpuService.crossShopPage(dto));
    }

    /**
     * 商品筛选维度聚合（分类 / 品牌）。⚠ 两维度互斥排除自身：分类维度不受已选分类影响、
     * 品牌维度不受已选品牌影响（否则选中后同维度选项即消失）。
     */
    @PostMapping("/facets")
    public RespData<StoreGoodsSpuFacetVO> facets(@RequestBody StoreGoodsSpuFacetQueryDTO dto) {
        return RespData.success(storeGoodsSpuService.facets(dto));
    }

    /**
     * 商品<b>批量</b>详情（跨店，不校验归属；含 SKU 列表与锁定信息，只读）。
     * <p>调用方是 <b>mall-bff 的购物车列表</b>：一次调用取回多个 SPU 的详情，替代逐行调单条详情
     * （购物车行数上限 100，逐行即 100 次往返），消除 N+1。SQL 条数与 {@code spuIds} 个数无关。</p>
     * <p>⚠ 查不到的 id（SPU 已删除）<b>跳过、不出现在出参里</b>，<b>不报 404</b>——与单条详情
     * 「取不到即报 400」刻意不同：购物车行可能引用已被删除的商品，逐行报错会让整个列表取不回来，
     * 故由调用方按「拿不到 = 商品不存在」处理。</p>
     * <p>⚠ 出参是<b>管理端超集</b>（含 {@code lockUser} / {@code goodsSpuId} 等），
     * <b>C 端输出前由 mall-bff 裁剪</b>（见 docs/contracts/store.md 第三节、cross-cutting 第 17/19/20 条）。</p>
     * <p>用 POST + body 而非 query 参数：{@code spuIds} 是集合（同 /cross-shop/spu/page 与 /facets 口径）。</p>
     */
    @PostMapping("/spu/batch")
    public RespData<List<StoreGoodsSpuPlatformDetailVO>> batchSpuDetail(
            @Validated @RequestBody StoreGoodsSpuBatchQueryDTO dto) {
        return RespData.success(storeGoodsSpuService.details(dto.getSpuIds()));
    }

    /**
     * 锁定商品（原因必填）：写锁定字段 + 名下 SKU 级联下架 → SPU 推导为下架；
     * 锁定期有作用域的那些写方法一律拒绝（整行只读）
     */
    @PostMapping("/spu/{id}/lock")
    public RespData<Void> lock(@PathVariable("id") Long id, @Validated @RequestBody StoreGoodsLockDTO dto) {
        storeGoodsSpuService.lock(id, dto);
        return RespData.success();
    }

    /**
     * 解锁商品：清空锁定字段；<b>不恢复上架</b>（SKU 保持下架，需店主手动上架）
     */
    @PostMapping("/spu/{id}/unlock")
    public RespData<Void> unlock(@PathVariable("id") Long id) {
        storeGoodsSpuService.unlock(id);
        return RespData.success();
    }
}
