package com.panoramic.goods.controller;

import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import com.panoramic.goods.dto.CategorySaveDTO;
import com.panoramic.goods.dto.CategoryUpdateDTO;
import com.panoramic.goods.service.CategoryService;
import com.panoramic.goods.vo.CategoryTreeVO;
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
 * 商品分类接口
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 新建分类
     */
    @PostMapping
    @PreAuthorize("hasAuthority('goods:category:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody CategorySaveDTO dto) {
        return RespData.success(categoryService.saveCategory(dto));
    }

    /**
     * 查询全量分类树
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('goods:category')")
    public RespData<List<CategoryTreeVO>> tree() {
        return RespData.success(categoryService.tree());
    }

    /**
     * 更新分类（仅名称与排序）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:category:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "分类ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody CategoryUpdateDTO dto) {
        categoryService.updateCategory(id, dto);
        return RespData.success();
    }

    /**
     * 删除分类（存在子分类或商品时拒绝）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:category:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "分类ID不能为空") Long id) {
        categoryService.deleteCategory(id);
        return RespData.success();
    }
}
