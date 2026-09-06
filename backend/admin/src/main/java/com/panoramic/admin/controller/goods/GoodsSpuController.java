package com.panoramic.admin.controller.goods;

import com.panoramic.admin.bff.GoodsTemplateBffService;
import com.panoramic.common.goods.dto.SpuPageQueryDTO;
import com.panoramic.common.goods.dto.SpuSaveDTO;
import com.panoramic.common.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.common.goods.dto.SpuStatusDTO;
import com.panoramic.common.goods.dto.SpuUpdateDTO;
import com.panoramic.common.goods.vo.PageResult;
import com.panoramic.common.goods.vo.SpuDetailVO;
import com.panoramic.common.goods.vo.SpuPageItemVO;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * admin 端 BFF · 标准商品模板维护编排接口（面向 admin 前端）。
 * <p>页面请求经网关 {@code /admin/goods/spu/**} → 本控制器 → 内部 Feign 调 goods-center。
 * 只做聚合/包装（RespData），不持有 goods 域实体与表。</p>
 */
@RestController
@RequestMapping("/goods/spu")
@RequiredArgsConstructor
public class GoodsSpuController {

    private final GoodsTemplateBffService goodsTemplateBffService;

    /**
     * 标准模板分页检索
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('goods:spu:list')")
    public RespData<PageResult<SpuPageItemVO>> page(@Validated SpuPageQueryDTO dto) {
        return RespData.success(goodsTemplateBffService.pageSpu(dto));
    }

    /**
     * 标准模板详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:spu:list')")
    public RespData<SpuDetailVO> detail(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        return RespData.success(goodsTemplateBffService.spuDetail(id));
    }

    /**
     * 新建模板
     */
    @PostMapping
    @PreAuthorize("hasAuthority('goods:spu:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody SpuSaveDTO dto) {
        return RespData.success(goodsTemplateBffService.saveSpu(dto));
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:spu:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody SpuUpdateDTO dto) {
        goodsTemplateBffService.updateSpu(id, dto);
        return RespData.success();
    }

    /**
     * 全量替换模板 SKU
     */
    @PutMapping("/{id}/skus")
    @PreAuthorize("hasAuthority('goods:spu:edit')")
    public RespData<Void> replaceSkus(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                      @RequestBody SpuSkuReplaceDTO dto) {
        goodsTemplateBffService.replaceSpuSkus(id, dto);
        return RespData.success();
    }

    /**
     * 模板展示/隐藏切换
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('goods:spu:edit')")
    public RespData<Void> updateStatus(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                       @Validated @RequestBody SpuStatusDTO dto) {
        goodsTemplateBffService.updateSpuStatus(id, dto);
        return RespData.success();
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:spu:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        goodsTemplateBffService.deleteSpu(id);
        return RespData.success();
    }
}
