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
     * 规格属性配置 JSON 字符串，如 [{"spec":"颜色","values":["黑色","白色"]}]
     */
    private String specConfig;

    /**
     * 展示状态：0 隐藏，1 展示（商品中台商品为商城商品信息模板，无上下架概念）
     */
    private Integer status;
}
