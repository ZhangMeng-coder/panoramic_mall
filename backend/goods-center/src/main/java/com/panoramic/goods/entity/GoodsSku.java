package com.panoramic.goods.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品 SKU 实体（规格属性组合，无价格库存）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goods_sku")
public class GoodsSku extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属 SPU ID
     */
    private Long spuId;

    /**
     * 规格属性组合 JSON 字符串，如 [{"spec":"颜色","value":"黑色"},{"spec":"内存","value":"256G"}]
     */
    private String specAttrs;

    /**
     * 商家自定义 SKU 编码（可选）
     */
    private String skuCode;

    /**
     * SKU 图片 URL（可选）
     */
    private String mainImage;

    /**
     * 版本戳（Unix 毫秒）：本 SKU 发生修改即刷新（业务字段，非审计字段，故显式赋值）
     */
    private Long version;
}
