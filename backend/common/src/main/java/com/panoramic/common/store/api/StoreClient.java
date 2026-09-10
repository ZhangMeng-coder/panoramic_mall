package com.panoramic.common.store.api;

import com.panoramic.common.store.dto.ShopAuditDTO;
import com.panoramic.common.store.dto.ShopPageQueryDTO;
import com.panoramic.common.store.dto.ShopSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.common.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

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
 * platform 方法（admin 触发）不传 store_id，全量操作。服务端以 X-User-Type 分流。
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
    // 说明：本组接口无 platform 对应物——本轮 admin 不做店铺商品管理（无 platform 侧）。
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
}
