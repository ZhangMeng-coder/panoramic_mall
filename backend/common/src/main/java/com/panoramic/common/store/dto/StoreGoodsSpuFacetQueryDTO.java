package com.panoramic.common.store.dto;

import lombok.Data;

import java.util.List;

/**
 * 商品筛选维度聚合查询参数（store 域内部接口与端 BFF 同源共享）
 */
@Data
public class StoreGoodsSpuFacetQueryDTO {

    /** 关键字（模糊匹配商品名称），空 = 不按关键字过滤 */
    private String keyword;

    /**
     * 范围锚点（已展开的子树分类 id 集合），空 = 不限。
     * <p>分类页传路由分类的子树；搜索页为空。</p>
     */
    private List<Long> scopeCategoryIds;

    /**
     * 已选分类筛选（已展开的子树分类 id 集合），空 = 未选。
     * <p>⚠ 只作用于 {@code brands} 维度，不作用于 {@code categories} 维度。</p>
     */
    private List<Long> filterCategoryIds;

    /**
     * 已选品牌筛选，空 = 未选。
     * <p>⚠ 只作用于 {@code categories} 维度，不作用于 {@code brands} 维度。</p>
     */
    private List<Long> filterBrandIds;

    /** 所属店铺审核状态，空 = 不过滤（C 端固定传 2） */
    private Integer shopStatus;

    /** 上下架，空 = 不过滤（C 端固定传 1） */
    private Integer shelfStatus;

    /** 锁定状态，空 = 不过滤（C 端固定传 0） */
    private Integer lockStatus;
}
