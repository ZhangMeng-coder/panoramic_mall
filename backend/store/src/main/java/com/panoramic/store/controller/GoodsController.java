package com.panoramic.store.controller;

import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
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

/**
 * 店铺在售商品内部领域接口（store 域下沉纯域）。
 * <p>仅供 store-bff 经内部 Feign（{@code /internal/store/goods/**}）调用，不对页面暴露公网路由；
 * 方法直接返回业务原类型（不包 RespData），错误经 {@code StoreDomainExceptionHandler}
 * 以真实 HTTP 状态码传播。权限判定与店铺审核门禁（{@code status == 2}，R9）已收敛在端 BFF，
 * 本接口只负责执行；写操作审计 user_id 由 {@code StoreUserIdentityFilter} 从 X-User-Id 直取填充。</p>
 * <p>全部接口均为 owner 侧：{@code storeId}（= 店主账号 id，账号店同 ID）由 store-bff 从登录态带入，
 * 域内以「id + store_id」双条件限定作用域（R11）；本切片无 platform 对应物。</p>
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
}
