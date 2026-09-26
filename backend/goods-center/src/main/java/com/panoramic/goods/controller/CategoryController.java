package com.panoramic.goods.controller;

import com.panoramic.contract.goods.dto.CategorySaveDTO;
import com.panoramic.contract.goods.dto.CategoryUpdateDTO;
import com.panoramic.contract.goods.vo.CategoryTreeVO;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
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
import java.util.Map;

/**
 * 标准商品平台 · 分类内部领域接口（goods-center 下沉纯域）。
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/goods/...}）调用，不再向页面暴露公网路由。
 * 出参一律包 {@code RespData<T>}（cross-cutting 第 2 条）：业务结果（含业务失败）走 HTTP 200 + {@code code}，
 * 异常由 common 的 {@code GlobalExceptionHandler} 兜底成 HTTP 500 —— 那是熔断唯一的失败信号（第 13 条）。
 * 权限判定已收敛在端 BFF（@PreAuthorize），本接口只负责执行；
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
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody CategorySaveDTO dto) {
        return RespData.success(categoryService.saveCategory(dto));
    }

    /**
     * 查询全量分类树
     */
    @GetMapping("/tree")
    public RespData<List<CategoryTreeVO>> tree() {
        return RespData.success(categoryService.tree());
    }

    /**
     * 批量取分类全路径（端 BFF 列表/详情读时解析分类全路径用）。
     * <p>入参为空集合时返回空 Map；结果只含命中的 id（查不到的不出现），由调用方回退快照名。</p>
     */
    @PostMapping("/paths")
    public RespData<Map<Long, String>> paths(@RequestBody List<Long> categoryIds) {
        return RespData.success(categoryService.pathNames(categoryIds));
    }

    /**
     * 更新分类（仅名称与排序）
     */
    @PutMapping("/{id}")
    public RespData<Void> update(@PathVariable Long id,
                                @Validated(ValidationGroups.Update.class) @RequestBody CategoryUpdateDTO dto) {
        categoryService.updateCategory(id, dto);
        return RespData.success();
    }

    /**
     * 删除分类（存在子分类或商品时拒绝）
     */
    @DeleteMapping("/{id}")
    public RespData<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return RespData.success();
    }
}
