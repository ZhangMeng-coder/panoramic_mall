package com.panoramic.admin.controller.goods;

import com.panoramic.admin.bff.GoodsTemplateBffService;
import com.panoramic.common.goods.dto.BrandPageQueryDTO;
import com.panoramic.common.goods.dto.BrandSaveDTO;
import com.panoramic.common.goods.dto.BrandUpdateDTO;
import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.PageResult;
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

import java.util.List;

/**
 * admin 端 BFF · 品牌维护编排接口（面向 admin 前端）。
 * <p>页面请求经网关 {@code /admin/goods/brands/**} → 本控制器 → 内部 Feign 调 goods-center。
 * 只做聚合/包装（RespData），不持有 goods 域实体与表。</p>
 */
@RestController
@RequestMapping("/goods/brands")
@RequiredArgsConstructor
public class GoodsBrandController {

    private final GoodsTemplateBffService goodsTemplateBffService;

    /**
     * 品牌分页查询
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('goods:brand:list')")
    public RespData<PageResult<BrandVO>> page(@Validated BrandPageQueryDTO dto) {
        return RespData.success(goodsTemplateBffService.pageBrands(dto));
    }

    /**
     * 全量品牌列表（商品表单下拉选择用）
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('goods:brand:list')")
    public RespData<List<BrandVO>> list() {
        return RespData.success(goodsTemplateBffService.listBrands());
    }

    /**
     * 品牌详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:brand:list')")
    public RespData<BrandVO> detail(@PathVariable @NotNull(message = "品牌ID不能为空") Long id) {
        return RespData.success(goodsTemplateBffService.brandDetail(id));
    }

    /**
     * 新建品牌
     */
    @PostMapping
    @PreAuthorize("hasAuthority('goods:brand:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody BrandSaveDTO dto) {
        return RespData.success(goodsTemplateBffService.saveBrand(dto));
    }

    /**
     * 更新品牌
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:brand:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "品牌ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody BrandUpdateDTO dto) {
        goodsTemplateBffService.updateBrand(id, dto);
        return RespData.success();
    }

    /**
     * 删除品牌（被商品引用时拒绝）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:brand:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "品牌ID不能为空") Long id) {
        goodsTemplateBffService.deleteBrand(id);
        return RespData.success();
    }
}
