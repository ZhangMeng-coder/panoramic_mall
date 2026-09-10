package com.panoramic.common.store.vo;

import com.panoramic.common.goods.dto.SpecAttr;
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
}
