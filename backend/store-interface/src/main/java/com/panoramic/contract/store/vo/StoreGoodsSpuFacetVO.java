package com.panoramic.contract.store.vo;

import lombok.Data;

import java.util.List;

/**
 * 商品筛选维度聚合结果（分类 / 品牌两个维度）。
 * <p><b>⚠ 核心口径：每个维度计算时排除自己那一维。</b>
 * {@code categories} 只受 {@code filterBrandIds} 影响（不受 {@code filterCategoryIds}），
 * {@code brands} 只受 {@code filterCategoryIds} 影响（不受 {@code filterBrandIds}）——
 * 否则用户每选一个选项，同维度的其它选项就会全部消失。</p>
 */
@Data
public class StoreGoodsSpuFacetVO {

    /** 分类维度可选项（按 count 降序，id 升序兜底） */
    private List<StoreGoodsFacetItemVO> categories;

    /** 品牌维度可选项（按 count 降序，id 升序兜底） */
    private List<StoreGoodsFacetItemVO> brands;
}
