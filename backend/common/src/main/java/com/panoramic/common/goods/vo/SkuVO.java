package com.panoramic.common.goods.vo;

import com.panoramic.common.goods.dto.SpecAttr;
import lombok.Data;

import java.util.List;

/**
 * SKU 响应
 */
@Data
public class SkuVO {

    /**
     * SKU ID
     */
    private Long id;

    /**
     * 所属 SPU ID
     */
    private Long spuId;

    /**
     * 规格属性组合
     */
    private List<SpecAttr> specAttrs;

    /**
     * 商家自定义 SKU 编码
     */
    private String skuCode;

    /**
     * SKU 图片 URL
     */
    private String mainImage;
}
