package com.panoramic.admin.controller.goods;

import com.panoramic.admin.bff.GoodsTemplateBffService;
import com.panoramic.common.goods.dto.CategorySaveDTO;
import com.panoramic.common.goods.dto.CategoryUpdateDTO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
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
 * admin 端 BFF · 分类维护编排接口（面向 admin 前端）。
 * <p>页面请求经网关 {@code /admin/goods/categories/**} → 本控制器 → 内部 Feign 调 goods-center。
 * 只做聚合/包装（RespData），不持有 goods 域实体与表。</p>
 */
@RestController
@RequestMapping("/goods/categories")
@RequiredArgsConstructor
public class GoodsCategoryController {

    private final GoodsTemplateBffService goodsTemplateBffService;

    /**
     * 新建分类
     */
    @PostMapping
    @PreAuthorize("hasAuthority('goods:category:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody CategorySaveDTO dto) {
        return RespData.success(goodsTemplateBffService.saveCategory(dto));
    }

    /**
     * 查询全量分类树
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('goods:category:list')")
    public RespData<List<CategoryTreeVO>> tree() {
        return RespData.success(goodsTemplateBffService.categoryTree());
    }

    /**
     * 更新分类（仅名称与排序）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:category:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "分类ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody CategoryUpdateDTO dto) {
        goodsTemplateBffService.updateCategory(id, dto);
        return RespData.success();
    }

    /**
     * 删除分类（存在子分类或商品时拒绝）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:category:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "分类ID不能为空") Long id) {
        goodsTemplateBffService.deleteCategory(id);
        return RespData.success();
    }
}
