package com.panoramic.goods.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分类实体（多级树）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goods_category")
public class GoodsCategory extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 父分类ID，0 表示顶级
     */
    private Long parentId;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 层级：1=顶级
     */
    private Integer level;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}
