package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量设置 SKU 库存请求参数（store 域内部接口与 store-bff 同源共享）。
 * <p>批量 = 把选中行<b>统一设为同一总库存值</b>（不做「增减 N」），不动 {@code warn_stock}。</p>
 */
@Data
public class StoreGoodsStockBatchUpdateDTO {

    /**
     * 待设置的 SKU id 集合（必填，至少一个）
     */
    @NotEmpty(message = "请至少选择一个 SKU")
    private List<Long> skuIds;

    /**
     * 统一设置的总库存（必填，≥0）
     */
    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;
}
