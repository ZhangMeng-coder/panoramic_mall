package com.panoramic.mallbff.dto;

import lombok.Data;

import java.util.List;

/**
 * C 端筛选维度聚合查询参数（页面契约，见 docs/contracts/mall-bff.md）。
 * <p>字段与 {@link MallGoodsPageQueryDTO} 的筛选部分一致（不含分页与排序：facets 与页无关），
 * 同样只表达页面选择，子树展开与 C 端展示口径由 {@code CatalogBffService} 补。</p>
 */
@Data
public class MallFacetQueryDTO {

    /**
     * 关键字
     */
    private String keyword;

    /**
     * 分类页路由锚点（可空）
     */
    private Long categoryId;

    /**
     * 已选分类筛选（多选）
     */
    private List<Long> categoryIds;

    /**
     * 已选品牌筛选（多选）
     */
    private List<Long> brandIds;
}
