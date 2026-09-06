package com.panoramic.common.goods.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品展示/隐藏状态请求参数（商品中台商品为信息模板，无上下架概念）
 */
@Data
public class SpuStatusDTO {

    /**
     * 展示状态：0 隐藏，1 展示
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值必须为 0（隐藏）或 1（展示）")
    @Max(value = 1, message = "状态取值必须为 0（隐藏）或 1（展示）")
    private Integer status;
}
