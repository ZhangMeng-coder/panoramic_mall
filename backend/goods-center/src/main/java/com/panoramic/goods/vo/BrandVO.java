package com.panoramic.goods.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌响应
 */
@Data
public class BrandVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 品牌名称
     */
    private String name;

    /**
     * 品牌 LOGO URL
     */
    private String logo;

    /**
     * 品牌简介
     */
    private String description;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
