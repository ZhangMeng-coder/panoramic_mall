package com.panoramic.common.store.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 店铺商品 SKU 上下架请求参数。
 * <p>SKU 上下架不受 SPU 状态限制，且反向驱动 SPU：上架任一 SKU → SPU 自动上架；
 * SKU 全部下架 → SPU 自动下架。</p>
 */
@Data
public class StoreGoodsSkuShelfDTO {

    /**
     * 上下架：0 下架，1 上架
     */
    @NotNull(message = "上下架状态不能为空")
    @Min(value = 0, message = "上下架状态不合法")
    @Max(value = 1, message = "上下架状态不合法")
    private Integer shelfStatus;
}
