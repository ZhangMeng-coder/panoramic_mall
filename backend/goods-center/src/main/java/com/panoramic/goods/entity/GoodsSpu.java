package com.panoramic.goods.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品 SPU 实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goods_spu")
public class GoodsSpu extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 所属分类ID（叶子分类）
     */
    private Long categoryId;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 轮播图 URL 数组 JSON 字符串，如 ["url1","url2"]
     */
    private String imageList;

    /**
     * 商品详情（富文本 HTML）
     */
    private String description;

    /**
     * 状态：0 下架，1 上架
     */
    private Integer status;
}
