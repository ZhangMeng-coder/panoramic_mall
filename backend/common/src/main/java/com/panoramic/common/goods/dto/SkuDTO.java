package com.panoramic.common.goods.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * SKU 请求参数（新建时 id 为空；更新时带 id 的为存量 SKU）
 */
@Data
public class SkuDTO {

    /**
     * SKU ID（新建为空）
     */
    private Long id;

    /**
     * 规格属性组合
     */
    @Valid
    @NotEmpty(message = "SKU 至少需要一个规格属性")
    private List<SpecAttr> specAttrs;

    /**
     * 商家自定义 SKU 编码（可选）
     */
    private String skuCode;

    /**
     * SKU 图片 URL（可选）
     */
    private String mainImage;
}
