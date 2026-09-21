package com.panoramic.contract.store.vo;

import com.panoramic.contract.store.dto.SpecAttr;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 店铺在售商品 SKU 响应
 */
@Data
public class StoreGoodsSkuVO {

    /**
     * SKU 主键
     */
    private Long id;

    /**
     * 所属店铺商品 SPU id
     */
    private Long spuId;

    /**
     * 规格属性组合
     */
    private List<SpecAttr> specAttrs;

    /**
     * SKU 编码
     */
    private String skuCode;

    /**
     * SKU 图片 URL
     */
    private String mainImage;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 上下架：0 下架，1 上架
     */
    private Integer shelfStatus;

    /**
     * 可用库存 = {@code stock}（{@code locked_stock} 已于 2026-09-21 废弃，不参与口径）。
     * <p>⚠ 这是「能卖几件」，不是库存表里的总库存；管理端的账实明细看库存管理页接口。</p>
     */
    private Integer availableStock;
}
