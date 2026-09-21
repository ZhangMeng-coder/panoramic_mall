package com.panoramic.trade.order.domain;

/**
 * 订单状态（todo 原话：「订单状态用 enum 来管理，字段为 3 个：name,mall,store/admin」）。
 *
 * <p>⚠ 三个字段的落法（裁定 D7）：Java 里 {@code name} 不能作字段名，枚举**常量名本身**即那个 name
 * （{@link #name()}），另两个字段命名为 {@link #mallLabel} / {@link #storeAdminLabel}。两侧文案是
 * <b>刻意不同</b>的：同一个状态在顾客眼里与在店主眼里是两件事（如 {@link #PAID}——顾客看到「已支付」，
 * 店主看到「待发货」，它描述的是同一刻的两个视角），故不能合成一个 label 省事。</p>
 *
 * <p>⚠ <b>顺序不写在本类里</b>：谁能走到谁由配置 {@code panoramic.trade.order.status-flow} 决定，
 * 由 {@link OrderStatusFlow} 装配并在装配期断言「配置覆盖本枚举全部常量」（缺一个即启动失败）。
 * 这样加状态只改配置与枚举，不用回头改 if/else 分支。</p>
 *
 * <p>⚠ 状态**已落库、已有接口**（阶段一，取代原先「纯模型」的 D1 口径）：{@code trade_order.status} 与
 * {@code trade_order_status_log.status} 两列都存 {@link #name()}（字符串）；两侧文案**不落库**，
 * 由 {@code TradeOrderVO} 读本枚举下发（{@link #mallLabel} / {@link #storeAdminLabel}），端 BFF 不另写一份。</p>
 */
public enum OrderStatus {

    /** 下单完成后的初始状态：顾客侧与店主侧都叫「待支付」 */
    PENDING_PAYMENT("待支付", "待支付"),

    /** 已付款：顾客看到「已支付」，店主看到的是待办「待发货」 */
    PAID("已支付", "待发货"),

    /** 已发货：顾客看到的是待办「待收货」，店主看到的是动作已完成「已发货」 */
    SHIPPED("已发货", "待收货"),

    /** 已收货（终态）：顾客侧「已收货」，店主侧的口径是这笔单完结「完成」 */
    RECEIVED("已收货", "完成");

    /** 商城端（C 端顾客）文案 */
    private final String mallLabel;

    /** 商户端 / 管理端文案 */
    private final String storeAdminLabel;

    OrderStatus(String mallLabel, String storeAdminLabel) {
        this.mallLabel = mallLabel;
        this.storeAdminLabel = storeAdminLabel;
    }

    /**
     * 商城端（顾客）可读文案——给页面直接展示，故措辞是用户视角（「已支付」）而不是内部术语
     */
    public String getMallLabel() {
        return mallLabel;
    }

    /**
     * 商户端 / 管理端可读文案——同一状态的店主视角（「待发货」），与 {@link #getMallLabel()} 刻意不同
     */
    public String getStoreAdminLabel() {
        return storeAdminLabel;
    }
}
