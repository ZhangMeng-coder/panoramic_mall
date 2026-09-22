package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发货请求参数。
 *
 * <p>⚠ 快递单号长度上限（{@code OrderModel.MAX_TRACKING_NO_LENGTH}，与 {@code trade_order.ship_no}
 * 列宽同口径）<b>不在本类收口</b>：唯一一份在域内，域会连「非空」一起判并给出带字段名的中文提示，
 * 错误同样是 400。本类的 {@code @NotBlank} 只拦结构性缺失。</p>
 *
 * <p>⚠ <b>锚点 {@code storeId} 必填</b>（cross-cutting 第 22 条）：写操作的作用域<b>没有</b>「合法全量视角」，
 * 省掉它就是「有权限给任意店铺的单发货」。值只能由端 BFF 从登录态取。</p>
 */
@Data
public class TradeOrderShipDTO {

    /**
     * 店铺 id（= 店主账号 id，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "店铺 id 不能为空")
    private Long storeId;

    /**
     * 快递单号（必填；取值上限见域内 {@code OrderModel.MAX_TRACKING_NO_LENGTH}）
     */
    @NotBlank(message = "快递单号不能为空")
    private String trackingNo;
}
