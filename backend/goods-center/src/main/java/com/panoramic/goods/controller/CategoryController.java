package com.panoramic.goods.controller;

import com.panoramic.common.goods.dto.CategorySaveDTO;
import com.panoramic.common.goods.dto.CategoryUpdateDTO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.goods.service.CategoryService;
import lombok.RequiredArgsConstructor;
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
 * 标准商品平台 · 分类内部领域接口（goods-center 下沉纯域）。
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/goods/...}）调用，不再向页面暴露公网路由；
 * 方法直接返回业务结果类型（不包 RespData），错误经 {@code GoodsDomainExceptionHandler}
 * 以真实 HTTP 状态码传播。权限判定已收敛在端 BFF（@PreAuthorize），本接口只负责执行；
 * 写操作的审计 user_id 由 {@code GoodsUserIdentityFilter} 从 BFF 透传的 X-User-Id 信任头直取填充。</p>
 */
@RestController
@RequestMapping("/internal/goods/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 新建分类
     */
    @PostMapping
    public Long save(@Validated(ValidationGroups.Create.class) @RequestBody CategorySaveDTO dto) {
        return categoryService.saveCategory(dto);
    }

    /**
     * 查询全量分类树
     */
    @GetMapping("/tree")
    public List<CategoryTreeVO> tree() {
        return categoryService.tree();
    }

    /**
     * 更新分类（仅名称与排序）
     */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id,
                       @Validated(ValidationGroups.Update.class) @RequestBody CategoryUpdateDTO dto) {
        categoryService.updateCategory(id, dto);
    }

    /**
     * 删除分类（存在子分类或商品时拒绝）
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }
}
