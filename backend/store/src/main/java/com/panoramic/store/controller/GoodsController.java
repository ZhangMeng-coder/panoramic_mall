package com.panoramic.store.controller;

import com.panoramic.contract.store.dto.StoreGoodsLockDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import com.panoramic.store.service.StoreGoodsSpuService;
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
 * 方法直接返回业务原类型（不包 RespData），错误经 {@code StoreDomainExceptionHandler}
 * 以真实 HTTP 状态码传播。权限判定与店铺审核门禁（{@code status == 2}，R9）已收敛在端 BFF，
 * 本接口只负责执行；写操作审计 user_id 由 {@code StoreUserIdentityFilter} 从 X-User-Id 直取填充。</p>
 * <p><b>owner 侧</b>（{@code /spu/**}，store-bff 调用）：{@code storeId}（= 店主账号 id，账号店同 ID）
 * 由 store-bff 从登录态带入，域内以「id + store_id」双条件限定作用域（R11）；
 * <b>platform 侧</b>（{@code /platform/spu/**}，admin BFF 调用）：不带 storeId、跨店全量，
 * 供管理后台「店铺商品管理」查看 / 锁定解锁。两侧分流由端 BFF 调哪一侧决定，域内不做身份判断。
 * 分页已通用化为 {@code /cross-shop/spu/page}（跨店通用，admin BFF 与 mall-bff 共用、差别只在传入条件），
 * {@code /platform/spu/**} 仅剩详情（单条 / 批量，其中批量为 mall-bff 购物车列表消费）与锁定解锁。</p>
 */
@RestController
@RequestMapping("/internal/store/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final StoreGoodsSpuService storeGoodsSpuService;

    /**
     * 我的商品分页（仅 storeId 名下的商品）
     */
    @GetMapping("/spu/page")
    public PageResult<StoreGoodsSpuPageItemVO> page(@RequestParam("storeId") Long storeId,
                                                    @Validated StoreGoodsSpuPageQueryDTO dto) {
        return storeGoodsSpuService.page(storeId, dto);
    }

    /**
     * 我的商品详情（含 SKU 列表）。注：本映射为字面量路径，优先于 /{id} 模板匹配。
     */
    @GetMapping("/spu/{id}")
    public StoreGoodsSpuDetailVO detail(@PathVariable("id") Long id,
                                        @RequestParam("storeId") Long storeId) {
        return storeGoodsSpuService.detail(storeId, id);
    }

    /**
     * 新增商品（可一并落 SKU；SPU 与 SKU 均以下架态起步）
     */
    @PostMapping("/spu")
    public Long save(@RequestParam("storeId") Long storeId,
                     @Validated @RequestBody StoreGoodsSpuSaveDTO dto) {
        return storeGoodsSpuService.save(storeId, dto);
    }

    /**
     * 修改商品（基础信息 + 规格属性配置；存在上架 SKU 时规格配置只读）
     */
    @PutMapping("/spu/{id}")
    public void update(@PathVariable("id") Long id,
                       @RequestParam("storeId") Long storeId,
                       @Validated @RequestBody StoreGoodsSpuUpdateDTO dto) {
        storeGoodsSpuService.update(storeId, id, dto);
    }

    /**
     * 删除商品（存在上架 SKU 时拒绝；否则软删并级联软删其下全部 SKU）
     */
    @DeleteMapping("/spu/{id}")
    public void delete(@PathVariable("id") Long id,
                       @RequestParam("storeId") Long storeId) {
        storeGoodsSpuService.delete(storeId, id);
    }

    /**
     * SKU 整单替换（未上架可增/改/删；已上架须原样保留且不得缺失）
     */
    @PutMapping("/spu/{id}/skus")
    public void replaceSkus(@PathVariable("id") Long id,
                            @RequestParam("storeId") Long storeId,
                            @Validated @RequestBody StoreGoodsSkuReplaceDTO dto) {
        storeGoodsSpuService.replaceSkus(storeId, id, dto);
    }

    /**
     * SKU 上下架（不受 SPU 状态限制，并反向联动 SPU 上下架）
     */
    @PutMapping("/spu/{spuId}/skus/{skuId}/shelf")
    public void updateSkuShelf(@PathVariable("spuId") Long spuId,
                               @PathVariable("skuId") Long skuId,
                               @RequestParam("storeId") Long storeId,
                               @Validated @RequestBody StoreGoodsSkuShelfDTO dto) {
        storeGoodsSpuService.updateSkuShelf(storeId, spuId, skuId, dto.getShelfStatus());
    }

    // ---- owner：SKU 库存（店铺端「库存管理」页；库存独立成表，读写只碰库存表）----

    /**
     * SKU 库存分页（仅 storeId 名下商品的 SKU；按 SKU 平铺一行一条，
     * 支持商品名 / SKU 编码关键字、上下架筛选、仅看低库存）
     */
    @GetMapping("/stock/page")
    public PageResult<StoreGoodsStockPageItemVO> pageSkuStock(@RequestParam("storeId") Long storeId,
                                                              @Validated StoreGoodsStockPageQueryDTO dto) {
        return storeGoodsSpuService.pageStock(storeId, dto);
    }

    /**
     * 改单行 SKU 库存（仅 storeId 名下）；{@code warnStock} 传 null = 清除预警。平台锁定期只读。
     */
    @PutMapping("/stock/{skuId}")
    public void updateSkuStock(@PathVariable("skuId") Long skuId, @RequestParam("storeId") Long storeId,
                               @Validated @RequestBody StoreGoodsStockUpdateDTO dto) {
        storeGoodsSpuService.updateSkuStock(storeId, skuId, dto);
    }

    /**
     * 批量设置整批 SKU 的总库存（仅 storeId 名下，单条 IN 更新）；平台锁定期只读。
     */
    @PutMapping("/stock/batch")
    public void batchUpdateSkuStock(@RequestParam("storeId") Long storeId,
                                    @Validated @RequestBody StoreGoodsStockBatchUpdateDTO dto) {
        storeGoodsSpuService.batchUpdateSkuStock(storeId, dto);
    }

    // ---- platform（不带 storeId，跨店通用；分页/聚合为 admin BFF 与 mall-bff 共用，详情/锁定为 admin 专有）----

    /**
     * 店铺商品分页（<b>跨店通用</b>：不带 storeId 锚点，调用方自设限定条件；
     * 分类与品牌为多值，categoryIds 由端 BFF 用分类树展开后传入）。
     * <p>admin BFF「店铺商品管理」不设限定条件（全量）；mall-bff C 端浏览固定传
     * shopStatus=2 + shelfStatus=1 + lockStatus=0。域侧不含 C 端隐含约束。</p>
     * <p>用 POST + body 而非 query 参数：categoryIds/brandIds 是集合，走 body 规避 @SpringQueryMap 的集合序列化问题。</p>
     */
    @PostMapping("/cross-shop/spu/page")
    public PageResult<StoreGoodsSpuCrossShopPageItemVO> crossShopPage(
            @Validated @RequestBody StoreGoodsSpuCrossShopPageQueryDTO dto) {
        return storeGoodsSpuService.crossShopPage(dto);
    }

    /**
     * 商品筛选维度聚合（分类 / 品牌）。⚠ 两维度互斥排除自身：分类维度不受已选分类影响、
     * 品牌维度不受已选品牌影响（否则选中后同维度选项即消失）。
     */
    @PostMapping("/facets")
    public StoreGoodsSpuFacetVO facets(@RequestBody StoreGoodsSpuFacetQueryDTO dto) {
        return storeGoodsSpuService.facets(dto);
    }

    /**
     * 店铺商品详情（跨店，不校验归属；含 SKU 列表与锁定信息，只读）
     */
    @GetMapping("/platform/spu/{id}")
    public StoreGoodsSpuPlatformDetailVO platformDetail(@PathVariable("id") Long id) {
        return storeGoodsSpuService.platformDetail(id);
    }

    /**
     * 店铺商品<b>批量</b>详情（跨店，不校验归属；含 SKU 列表与锁定信息，只读）。
     * <p>调用方是 <b>mall-bff 的购物车列表</b>：一次调用取回多个 SPU 的详情，替代逐行调单条详情
     * （购物车行数上限 100，逐行即 100 次往返），消除 N+1。SQL 条数与 {@code spuIds} 个数无关。</p>
     * <p>⚠ 查不到的 id（SPU 已删除）<b>跳过、不出现在出参里</b>，<b>不报 404</b>——与单条
     * {@link #platformDetail} 的「取不到即报错」刻意不同：购物车行可能引用已被删除的商品，
     * 逐行 404 会让整个列表取不回来，故由调用方按「拿不到 = 商品不存在」处理。</p>
     * <p>⚠ 出参是<b>管理端超集</b>（含 {@code lockUser} / {@code goodsSpuId} 等），
     * <b>C 端输出前由 mall-bff 裁剪</b>（见 docs/contracts/store.md 第三节、cross-cutting 第 17/19/20 条）。</p>
     * <p>用 POST + body 而非 query 参数：{@code spuIds} 是集合（同 /cross-shop/spu/page 与 /facets 口径）。</p>
     */
    @PostMapping("/platform/spu/batch")
    public List<StoreGoodsSpuPlatformDetailVO> platformSpuBatch(
            @Validated @RequestBody StoreGoodsSpuBatchQueryDTO dto) {
        return storeGoodsSpuService.platformDetails(dto.getSpuIds());
    }

    /**
     * 锁定商品（平台，原因必填）：写锁定字段 + 名下 SKU 级联下架 → SPU 推导为下架；
     * 锁定期 owner 侧整行只读
     */
    @PostMapping("/platform/spu/{id}/lock")
    public void lock(@PathVariable("id") Long id, @Validated @RequestBody StoreGoodsLockDTO dto) {
        storeGoodsSpuService.lock(id, dto);
    }

    /**
     * 解锁商品（平台）：清空锁定字段；<b>不恢复上架</b>（SKU 保持下架，需店主手动上架）
     */
    @PostMapping("/platform/spu/{id}/unlock")
    public void unlock(@PathVariable("id") Long id) {
        storeGoodsSpuService.unlock(id);
    }
}
