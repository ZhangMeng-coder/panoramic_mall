package com.panoramic.storebff.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 店铺端 BFF · 发货请求参数（**页面入参**，本层私有）。
 *
 * <p>⚠ <b>没有 {@code storeId} 字段</b>（cross-cutting 第 22 条）：锚点由 {@code StoreOrderBffService}
 * 从登录态取后写进<b>域侧</b>入参 {@code TradeOrderShipDTO.storeId}（域侧那里是 {@code @NotNull}，
 * 缺了即 HTTP 400）。页面能传来的锚点等于把「给任意店铺的单发货」的权限交给页面。</p>
 *
 * <p>⚠ 快递单号长度上限不在本类收口：唯一一份在域内（与 {@code trade_order.ship_no} 列宽同口径），
 * 域会连「非空」一起判并给出带字段名的中文提示，错误同样是 400。本类的 {@code @NotBlank}
 * 只拦结构性缺失。</p>
 */
@Data
public class StoreOrderShipDTO {

    /**
     * 快递单号（必填；取值上限见域内口径）
     */
    @NotBlank(message = "快递单号不能为空")
    private String trackingNo;
}
