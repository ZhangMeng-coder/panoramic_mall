package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 店铺在售商品 SKU 实体。
 * <p>与中台标准 SKU 同构（规格组合 + 编码 + 图片），额外带价格 {@code price}
 * （中台模板无价格、无库存；本域同样不建库存列）。</p>
 * <p>不带 {@code store_id}：归属经 {@code spu_id → store_goods_spu.store_id} 判定，
 * SKU 侧操作一律先校验其 SPU 归属（R11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_sku")
public class StoreGoodsSku extends BaseEntity {

    /** 上下架：下架 */
    public static final int SHELF_OFF = 0;
    /** 上下架：上架 */
    public static final int SHELF_ON = 1;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属店铺商品 SPU id
     */
    private Long spuId;

    /**
     * 规格属性组合 JSON 字符串（实体为 String，VO 层转 List）
     */
    private String specAttrs;

    /**
     * SKU 编码
     */
    private String skuCode;

    /**
     * SKU 图片 URL
     */
    private String mainImage;

    /**
     * 价格（必填，≥0.01）
     */
    private BigDecimal price;

    /**
     * 上下架：0 下架，1 上架（未上架可增改删；已上架整行锁死，须先下架）
     */
    private Integer shelfStatus;
}
