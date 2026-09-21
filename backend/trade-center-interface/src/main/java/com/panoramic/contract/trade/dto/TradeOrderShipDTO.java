package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发货请求参数（商户侧）。
 *
 * <p>⚠ 快递单号长度上限（{@code OrderModel.MAX_TRACKING_NO_LENGTH}，与 {@code trade_order.ship_no}
 * 列宽同口径）<b>不在本类收口</b>：唯一一份在域内，域会连「非空」一起判并给出带字段名的中文提示，
 * 错误同样是 400。本类的 {@code @NotBlank} 只拦结构性缺失。</p>
 */
@Data
public class TradeOrderShipDTO {

    /**
     * 快递单号（必填；取值上限见域内 {@code OrderModel.MAX_TRACKING_NO_LENGTH}）
     */
    @NotBlank(message = "快递单号不能为空")
    private String trackingNo;
}
