package com.panoramic.common.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单行 SKU 库存修改请求参数（store 域内部接口与 store-bff 同源共享）。
 * <p>本期是直接赋值，不是增减；占用库存 {@code locked_stock} 由交易域维护，不在此入参内。</p>
 */
@Data
public class StoreGoodsStockUpdateDTO {

    /**
     * 总库存（必填，≥0）
     */
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;

    /**
     * 低库存预警阈值（≥0）；<b>可空 = 清除预警</b>
     */
    @Min(value = 0, message = "预警值不能为负")
    private Integer warnStock;
}
