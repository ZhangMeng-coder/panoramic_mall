package com.panoramic.goods.dto;

import lombok.Data;

/**
 * 单个规格属性（同一 SKU 内 spec 名不重复）
 */
@Data
public class SpecAttr {

    /**
     * 规格名，如"颜色"
     */
    private String spec;

    /**
     * 规格值，如"曜石黑"
     */
    private String value;
}
