package com.panoramic.goods.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品（SPU）分页列表项响应
 */
@Data
public class SpuPageItemVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 品牌名称
     */
    private String brandName;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 展示状态：0 隐藏，1 展示
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
