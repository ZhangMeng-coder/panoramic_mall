package com.panoramic.goods.dto;

import lombok.Data;

import java.util.List;

/**
 * 商品规格属性配置项：一个规格维度及其允许的属性值
 */
@Data
public class SpecConfigItem {

    /**
     * 规格名，如 "颜色"
     */
    private String spec;

    /**
     * 该规格允许的属性值，如 ["黑色", "白色"]
     */
    private List<String> values;
}
