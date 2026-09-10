package com.panoramic.common.store.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺在售商品分页列表项
 * <p>分类/品牌名称取落库时的快照，不再回查中台。</p>
 */
@Data
public class StoreGoodsSpuPageItemVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 分类名称快照
     */
    private String categoryName;

    /**
     * 品牌名称快照
     */
    private String brandName;

    /**
     * 上下架：0 下架，1 上架
     */
    private Integer shelfStatus;

    /**
     * SKU 数量
     */
    private Integer skuCount;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
