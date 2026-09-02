package com.panoramic.goods.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商品上下架状态请求参数
 */
@Data
public class SpuStatusDTO {

    /**
     * 状态：0 下架，1 上架
     */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态取值必须为 0（下架）或 1（上架）")
    @Max(value = 1, message = "状态取值必须为 0（下架）或 1（上架）")
    private Integer status;
}
