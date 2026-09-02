package com.panoramic.goods.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品品牌实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goods_brand")
public class GoodsBrand extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
}
