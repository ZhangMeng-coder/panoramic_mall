package com.panoramic.storebff.controller.goods;

import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.goods.vo.SpuBySkuCodeVO;
import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuShelfDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.common.vo.RespData;
import com.panoramic.storebff.bff.StoreGoodsBffService;
import com.panoramic.storebff.vo.StoreGoodsSpuDetailBffVO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * 店铺端 BFF · 店铺在售商品接口（面向店铺端前端）。
 * <p>页面请求经网关 {@code /store/goods/**} → 本控制器 → 内部 Feign 调 store 域（商品数据）
 * 与 goods-center（分类/品牌下拉、按 SKU 编码反查中台模板）。只做聚合/包装（RespData），
 * 不持有任何域实体与表。</p>
 * <p><b>无 {@code @PreAuthorize}</b>：店铺端不做 RBAC（登录态由 common 认证链保证），
 * 业务门禁（店铺 {@code status == 2}，R9）在 {@link StoreGoodsBffService} 统一前置。</p>
 */
@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
@Validated
public class GoodsController {

    private final StoreGoodsBffService storeGoodsBffService;

    /**
     * 我的商品分页
     */
    @GetMapping("/spu/page")
    public RespData<PageResult<StoreGoodsSpuPageItemVO>> page(@Validated StoreGoodsSpuPageQueryDTO dto) {
        return RespData.success(storeGoodsBffService.page(dto));
    }

    /**
     * 我的商品详情（含中台关联版本比对结果，供「同步」提示）
     */
    @GetMapping("/spu/{id}")
    public RespData<StoreGoodsSpuDetailBffVO> detail(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        return RespData.success(storeGoodsBffService.detail(id));
    }

    /**
     * 新增商品（可一并落 SKU）
     */
    @PostMapping("/spu")
    public RespData<Long> save(@Validated @RequestBody StoreGoodsSpuSaveDTO dto) {
        return RespData.success(storeGoodsBffService.save(dto));
    }

    /**
     * 修改商品（基础信息 + 规格配置）
     */
    @PutMapping("/spu/{id}")
    public RespData<Void> update(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                 @Validated @RequestBody StoreGoodsSpuUpdateDTO dto) {
        storeGoodsBffService.update(id, dto);
        return RespData.success();
    }

    /**
     * 删除商品（存在上架 SKU 时拒绝）
     */
    @DeleteMapping("/spu/{id}")
    public RespData<Void> delete(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        storeGoodsBffService.delete(id);
        return RespData.success();
    }

    /**
     * SKU 整单替换（未上架可增/改/删；已上架须原样保留）
     */
    @PutMapping("/spu/{id}/skus")
    public RespData<Void> replaceSkus(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                      @Validated @RequestBody StoreGoodsSkuReplaceDTO dto) {
        storeGoodsBffService.replaceSkus(id, dto);
        return RespData.success();
    }

    /**
     * SKU 上下架（上架任一 SKU 联动 SPU 上架；全下架联动 SPU 下架）
     */
    @PutMapping("/spu/{spuId}/skus/{skuId}/shelf")
    public RespData<Void> updateSkuShelf(@PathVariable @NotNull(message = "商品ID不能为空") Long spuId,
                                         @PathVariable @NotNull(message = "SKU ID不能为空") Long skuId,
                                         @Validated @RequestBody StoreGoodsSkuShelfDTO dto) {
        storeGoodsBffService.updateSkuShelf(spuId, skuId, dto);
        return RespData.success();
    }

    /**
     * 分类树（来自中台，商品分类下拉）
     */
    @GetMapping("/categories/tree")
    public RespData<List<CategoryTreeVO>> categoryTree() {
        return RespData.success(storeGoodsBffService.categoryTree());
    }

    /**
     * 品牌列表（来自中台，商品品牌下拉，非必填）
     */
    @GetMapping("/brands")
    public RespData<List<BrandVO>> listBrands() {
        return RespData.success(storeGoodsBffService.listBrands());
    }

    /**
     * 按 SKU 编码反查中台标准模板（新增商品填 SKU_CODE 时预填整单）。
     * <p>未命中返回 {@code data.spu == null}（成功响应，非错误），前端提示后允许继续自建；
     * {@code data.matchedSkuCount > 1} 表示中台编码重复、已取第一条。</p>
     */
    @GetMapping("/center/spu-by-sku-code")
    public RespData<SpuBySkuCodeVO> centerSpuBySkuCode(@RequestParam("skuCode")
                                                       @NotBlank(message = "SKU编码不能为空") String skuCode) {
        return RespData.success(storeGoodsBffService.centerSpuBySkuCode(skuCode));
    }
}
