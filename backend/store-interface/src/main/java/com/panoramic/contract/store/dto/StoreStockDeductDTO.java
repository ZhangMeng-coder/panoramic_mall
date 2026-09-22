package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 交易侧库存扣减参数（store 域内部接口与调用方同源共享）。
 * <p>用途：<b>trade-center 的订单流水线</b>下单时扣减库存。数量恒正——扣减方向由「这是扣减接口」
 * 表达，不需要负数（回补走独立的按单回补接口，见 cross-cutting 第 24 条）。</p>
 * <p>⚠ <b>没有作用域字段</b>：调用方是<b>域</b>（trade-center），按资源 id 操作，
 * 域内不判身份、不校验店铺归属、不判平台锁定（可见性由交易侧的商品校验步骤判，R19）。</p>
 */
@Data
public class StoreStockDeductDTO {

    /**
     * SKU id（必填）
     */
    @NotNull(message = "SKU 不能为空")
    private Long skuId;

    /**
     * 扣减数量（必填，≥1）
     */
    @NotNull(message = "扣减数量不能为空")
    @Min(value = 1, message = "扣减数量至少为 1")
    private Integer quantity;

    /**
     * 订单号（必填；库存流水的配对键，回补按它找回该单扣过的行）
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
