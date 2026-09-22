package com.panoramic.contract.trade.dto;

import lombok.Data;

/**
 * 订单详情查询参数（**所有调用方共用一份**，cross-cutting 第 22 条）：只承载作用域。
 *
 * <p>⚠ 订单标识 {@code orderNo} **不在本类里**——它是资源标识，走路径变量（{@code GET /order/{orderNo}}），
 * 按第 23 条「路径变量不并入 DTO」。</p>
 *
 * <p>⚠ <b>两个作用域字段都可省</b>：订单详情是**有合法全量视角**的能力（管理端要看任意一笔），
 * 故省略即「不限定」；顾客侧 / 商户侧由端 BFF 从登录态填各自的 id。
 * 值<b>禁止</b>从前端入参透传（第 22 条）。</p>
 *
 * <p>⚠ 写操作（支付 / 发货 / 收货）<b>不用本类</b>：它们没有合法全量视角，作用域必须必填
 * ——那份「必填」由各自的入参 DTO（{@link TradeOrderPayDTO} / {@link TradeOrderShipDTO} /
 * {@link TradeOrderReceiveDTO}）用 {@code @NotNull} 表达。</p>
 */
@Data
public class TradeOrderQueryDTO {

    /**
     * 顾客 id（= {@code mall_user.id}，数据**作用域**）；不填则不限定
     */
    private Long customerId;

    /**
     * 店铺 id（= 店主账号 id，数据**作用域**）；不填则不限定
     */
    private Long storeId;
}
