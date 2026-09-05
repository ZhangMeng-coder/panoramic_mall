package com.panoramic.goods.controller;

import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import com.panoramic.goods.dto.BrandPageQueryDTO;
import com.panoramic.goods.dto.BrandSaveDTO;
import com.panoramic.goods.dto.BrandUpdateDTO;
import com.panoramic.goods.service.BrandService;
import com.panoramic.goods.vo.BrandVO;
import com.panoramic.goods.vo.PageResult;
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
 * 商品品牌接口
 */
@RestController
@RequestMapping("/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /**
     * 品牌分页查询
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('goods:brand')")
    public RespData<PageResult<BrandVO>> page(@Validated BrandPageQueryDTO dto) {
        return RespData.success(brandService.page(dto));
    }

    /**
     * 全量品牌列表（商品表单下拉选择用）
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('goods:brand')")
    public RespData<List<BrandVO>> list() {
        return RespData.success(brandService.listAll());
    }

    /**
     * 品牌详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:brand')")
    public RespData<BrandVO> detail(@PathVariable @NotNull(message = "品牌ID不能为空") Long id) {
        return RespData.success(brandService.detail(id));
    }

    /**
     * 新建品牌
     */
    @PostMapping
    @PreAuthorize("hasAuthority('goods:brand:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody BrandSaveDTO dto) {
        return RespData.success(brandService.saveBrand(dto));
    }

    /**
     * 更新品牌
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:brand:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "品牌ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody BrandUpdateDTO dto) {
        brandService.updateBrand(id, dto);
        return RespData.success();
    }

    /**
     * 删除品牌（被商品引用时拒绝）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:brand:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "品牌ID不能为空") Long id) {
        brandService.deleteBrand(id);
        return RespData.success();
    }
}
