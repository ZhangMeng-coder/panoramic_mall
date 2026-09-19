package com.panoramic.contract.store.api;

import com.panoramic.contract.store.dto.ShopAuditDTO;
import com.panoramic.contract.store.dto.ShopPageQueryDTO;
import com.panoramic.contract.store.dto.ShopSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsLockDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopOptionVO;
import com.panoramic.contract.store.vo.ShopVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * store（店铺业务域，下沉纯域）内部 Feign 客户端。
 * <p>本切片起 store 域只持 {@code store_shop}（账号店同 ID：店铺主键 == 店主账号 id），不再向页面暴露公网路由，
 * 由各端 BFF 经本接口内部调用。规约（见 CLAUDE.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 common（store 域服务端、store-bff/admin 客户端引用同一份类型）；</li>
 *   <li>方法直接返回业务结果类型（不包 RespData），错误走异常统一传播；</li>
 *   <li>调用经 {@link StoreFeignConfiguration} 附带信任头 + 透传主身份 + 熔断 + 错误解码。</li>
 * </ul>
 * 数据权限口径（D5）：owner 方法（store-bff 触发）强制携带 store_id，store 域只作用于「id==store_id 的店」；
 * platform 方法（admin 触发）不传 store_id，全量操作。owner / platform 的分流由「端 BFF 调哪一侧方法」
 * 决定，域内不做身份判断（不读 X-User-Type 判权；该头只用于审计留痕）。
 * 服务端路径与映射需与 store 域内部控制器一一对应（前缀 /internal/store）。</p>
 */
@FeignClient(name = "store", contextId = "storeClient",
        path = "/internal/store", configuration = StoreFeignConfiguration.class)
public interface StoreClient {

    // ---- owner（store-bff 调用，必带 store_id；X-User-Type=store）----

    /**
     * 我的店铺（store_id == 店主账号 id）。无店返回 HTTP 200 空 body → Feign 解出 null；
     * 调用方以 null 判定「未开店」。
     */
    @GetMapping("/shops/mine")
    ShopVO mineShop(@RequestParam("storeId") Long storeId);

    /**
     * 保存草稿（无店则建 id=storeId 的店；待审核/已通过状态机守卫）
     */
    @PostMapping("/shops/{storeId}/save")
    void saveShop(@PathVariable("storeId") Long storeId, @RequestBody ShopSaveDTO dto);

    /**
     * 提交审核（完整资质校验 → 待审核）
     */
    @PostMapping("/shops/{storeId}/submit")
    void submitShop(@PathVariable("storeId") Long storeId, @RequestBody ShopSaveDTO dto);

    // ---- platform（admin 调用，不传 store_id，全量；X-User-Type=admin）----

    /**
     * 店铺分页（状态/关键字筛选，全量）
     */
    @GetMapping("/shops/page")
    PageResult<ShopVO> pageShops(@SpringQueryMap ShopPageQueryDTO dto);

    /**
     * 店铺详情（无店主账号信息）
     */
    @GetMapping("/shops/{id}")
    ShopVO shopDetail(@PathVariable("id") Long id);

    /**
     * 审核通过/驳回（仅对待审核条件更新）
     */
    @PostMapping("/shops/{id}/audit")
    void auditShop(@PathVariable("id") Long id, @RequestBody ShopAuditDTO dto);

    // ---- owner：店铺在售商品（store-bff 调用，必带 store_id；X-User-Type=store）----
    // 说明：owner 侧只作用于 store_id 名下的商品，由 store-bff 从登录态取 store_id 传入；
    // platform 侧对应物见本文件尾部同名 platform 方法（不传 store_id、全量，admin BFF 调用），
    // 两侧分流由「端 BFF 调哪一侧」决定，域内不做身份判断（D5）。
    // 出参 StoreGoodsSpuDetailVO 不含中台版本比对结果，由 store-bff 编排时补充。

    /**
     * 我的商品分页（仅 store_id 名下的商品）
     */
    @GetMapping("/goods/spu/page")
    PageResult<StoreGoodsSpuPageItemVO> pageStoreGoods(@RequestParam("storeId") Long storeId,
                                                       @SpringQueryMap StoreGoodsSpuPageQueryDTO dto);

    /**
     * 我的商品详情（含 SKU 列表）
     */
    @GetMapping("/goods/spu/{id}")
    StoreGoodsSpuDetailVO storeGoodsDetail(@PathVariable("id") Long id, @RequestParam("storeId") Long storeId);

    /**
     * 新增商品（返回新商品 id；SKU 一律以下架态落库）
     */
    @PostMapping("/goods/spu")
    Long saveStoreGoods(@RequestParam("storeId") Long storeId, @RequestBody StoreGoodsSpuSaveDTO dto);

    /**
     * 修改商品（存在上架 SKU 时规格配置只读；centerVersion 非空则刷新关联版本戳）
     */
    @PutMapping("/goods/spu/{id}")
    void updateStoreGoods(@PathVariable("id") Long id, @RequestParam("storeId") Long storeId,
                          @RequestBody StoreGoodsSpuUpdateDTO dto);

    /**
     * 删除商品（存在上架 SKU 时拒绝；否则软删并级联软删其下全部 SKU）
     */
    @DeleteMapping("/goods/spu/{id}")
    void deleteStoreGoods(@PathVariable("id") Long id, @RequestParam("storeId") Long storeId);

    /**
     * SKU 整单替换（未上架可增/改/删；已上架必须原样保留且不得缺失）
     */
    @PutMapping("/goods/spu/{id}/skus")
    void replaceStoreGoodsSkus(@PathVariable("id") Long id, @RequestParam("storeId") Long storeId,
                               @RequestBody StoreGoodsSkuReplaceDTO dto);

    /**
     * SKU 上下架（驱动所属 SPU 状态联动）
     */
    @PutMapping("/goods/spu/{spuId}/skus/{skuId}/shelf")
    void updateStoreGoodsSkuShelf(@PathVariable("spuId") Long spuId, @PathVariable("skuId") Long skuId,
                                  @RequestParam("storeId") Long storeId,
                                  @RequestBody StoreGoodsSkuShelfDTO dto);

    /**
     * SKU 库存分页（owner 侧，仅 store_id 名下；按 SKU 平铺一行一条，
     * 支持商品名 / SKU 编码关键字、上下架筛选、仅看低库存）。
     * <p>出参的 {@code stock} 是库存表里的总库存；可用库存 = {@code stock - lockedStock}。</p>
     */
    @GetMapping("/goods/stock/page")
    PageResult<StoreGoodsStockPageItemVO> pageSkuStock(@RequestParam("storeId") Long storeId,
                                                       @SpringQueryMap StoreGoodsStockPageQueryDTO dto);

    /**
     * 改单行 SKU 库存（owner 侧，仅 store_id 名下）；{@code warnStock} 传 null = 清除预警。
     * <p>平台锁定期 owner 侧只读（域内强制拒绝），库存行不存在时按 0 行处理。</p>
     */
    @PutMapping("/goods/stock/{skuId}")
    void updateSkuStock(@PathVariable("skuId") Long skuId, @RequestParam("storeId") Long storeId,
                        @RequestBody StoreGoodsStockUpdateDTO dto);

    /**
     * 批量设置整批 SKU 的总库存（owner 侧，单条 IN 更新、不逐行）；平台锁定期只读。
     * <p>不属于本店的 SKU 直接拒绝（不静默跳过）。</p>
     */
    @PutMapping("/goods/stock/batch")
    void batchUpdateSkuStock(@RequestParam("storeId") Long storeId,
                             @RequestBody StoreGoodsStockBatchUpdateDTO dto);

    // ---- platform：店铺在售商品（跨店通用 + 管理端专有的详情/锁定；admin BFF 与 mall-bff 调用）----
    // 分页（/goods/cross-shop/spu/page）**跨店通用**：不传 store_id 锚点，调用方自设限定条件
    //   —— admin BFF「店铺商品管理」走全量，mall-bff C 端浏览固定传 shopStatus/shelfStatus/lockStatus。
    // 详情与锁定解锁（/goods/platform/spu/**）仍只服务管理端。
    // 锁定语义：锁定 → 名下 SKU 全部级联下架、SPU 随之推导为下架；锁定期 owner 侧整行只读；
    // 仅平台可解锁，解锁不自动恢复上架（由店主手动重新上架）。

    /**
     * 店铺商品分页（<b>跨店通用</b>：不传 store_id 锚点，调用方自设限定条件）。
     * <p>admin BFF 用于「店铺商品管理」（不限条件，全量）；
     * mall-bff 用于 C 端商品浏览（固定传 shopStatus=2 + shelfStatus=1 + lockStatus=0）。</p>
     * <p>⚠ 用 {@code POST + @RequestBody} 而非 query 参数：{@code categoryIds}/{@code brandIds} 是集合，
     * {@code @SpringQueryMap} 对集合字段的序列化口径不确定，走 body 规避。</p>
     */
    @PostMapping("/goods/cross-shop/spu/page")
    PageResult<StoreGoodsSpuCrossShopPageItemVO> pageStoreGoodsCrossShop(
            @RequestBody StoreGoodsSpuCrossShopPageQueryDTO dto);

    /**
     * 商品筛选维度聚合（分类 / 品牌，各带命中数）。
     * <p>⚠ 口径见 {@link StoreGoodsSpuFacetVO}：两维度互斥排除自身。</p>
     */
    @PostMapping("/goods/facets")
    StoreGoodsSpuFacetVO crossShopFacets(@RequestBody StoreGoodsSpuFacetQueryDTO dto);

    /**
     * 店铺商品详情（跨店，不校验归属；含 SKU 列表与锁定信息，只读）
     */
    @GetMapping("/goods/platform/spu/{id}")
    StoreGoodsSpuPlatformDetailVO platformStoreGoodsDetail(@PathVariable("id") Long id);

    /**
     * 锁定商品（原因必填）：写锁定字段 + 名下 SKU 级联下架 → SPU 推导为下架
     */
    @PostMapping("/goods/platform/spu/{id}/lock")
    void lockStoreGoods(@PathVariable("id") Long id, @RequestBody StoreGoodsLockDTO dto);

    /**
     * 解锁商品：清空锁定字段；<b>不恢复上架</b>（SKU 保持下架，需店主手动上架）
     */
    @PostMapping("/goods/platform/spu/{id}/unlock")
    void unlockStoreGoods(@PathVariable("id") Long id);

    /**
     * 店铺下拉选项（店铺商品列表「按店铺筛选」用）。
     * <p>不按审核状态过滤：未审核通过的店铺本就没有商品。</p>
     */
    @GetMapping("/shops/options")
    List<ShopOptionVO> listShopOptions();
}
