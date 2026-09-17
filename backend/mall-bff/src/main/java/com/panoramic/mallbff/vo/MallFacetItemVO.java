package com.panoramic.mallbff.vo;

import lombok.Data;

/**
 * C 端筛选面板的一个可选项（分类 / 品牌通用，页面契约见 docs/contracts/mall-bff.md）
 */
@Data
public class MallFacetItemVO {

    /**
     * 维度值 id（分类 id 或品牌 id）
     */
    private Long id;

    /**
     * 维度值名称
     */
    private String name;

    /**
     * 该维度值下的命中商品数
     */
    private Integer count;
}
