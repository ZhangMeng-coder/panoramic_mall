package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单行 SKU 库存修改请求参数（store 域内部接口与 store-bff 同源共享）。
 * <p>本期是直接赋值，不是增减；占用库存 {@code locked_stock} 已于 2026-09-21 废弃（不参与口径、不再写入），
 * 不在本入参内。</p>
 */
@Data
public class StoreGoodsStockUpdateDTO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id）。
     * <p>⚠ 值由端 BFF 自登录态取（{@code LoginUser.getId()}）并**无条件覆盖**，页面不得提供；
     * 只在域入口必填（{@link StoreScopeGroup}），见 cross-cutting 第 22 条。</p>
     */
    @NotNull(message = "店铺ID不能为空", groups = StoreScopeGroup.class)
    private Long storeId;

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
