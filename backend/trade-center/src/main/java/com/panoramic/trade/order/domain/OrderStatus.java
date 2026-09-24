package com.panoramic.trade.order.domain;

import java.util.List;

/**
 * 订单状态（todo 原话：「订单状态用 enum 来管理，字段为 3 个：name,mall,store/admin」）。
 *
 * <p>⚠ 三个字段的落法（裁定 D7）：Java 里 {@code name} 不能作字段名，枚举**常量名本身**即那个 name
 * （{@link #name()}），另两个字段命名为 {@link #mallLabel} / {@link #storeAdminLabel}。两侧文案是
 * <b>刻意不同</b>的：同一个状态在顾客眼里与在店主眼里是两件事（如 {@link #PAID}——顾客看到「已支付」，
 * 店主看到「待发货」，它描述的是同一刻的两个视角），故不能合成一个 label 省事。</p>
 *
 * <p>⚠ <b>「谁能走到谁」不写在本类里</b>，分两处写（2026-09-24 起的口径）：
 * <b>主链</b>（待支付 → 已支付 → 已发货 → 已收货）由配置 {@code panoramic.trade.order.status-flow} 给出，
 * 形状是**有序列表**；<b>两个结束过程</b>（{@link #PENDING_PAYMENT} → {@link #CANCELLED}、
 * {@link #PAID} → {@link #REFUNDED}）写死在 {@link OrderModel} 的那两个动作方法里
 * （它们是那两个动作的定义本身，不是可配流程），配置里不许再写这两个状态。
 * {@link OrderStatusFlow} 装配并在装配期断言「主链 + 两个结束过程的落点覆盖本枚举全部常量」
 * （缺一个即启动失败）。这样改主链只改配置与枚举，不用回头改 if/else 分支。</p>
 *
 * <p>⚠ <b>常量声明顺序不承载任何语义</b>：状态的先后由上面那份**有序列表**表达，不靠枚举声明顺序
 * （末两个不参与主链配置，排在最后只是读起来顺）。</p>
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
    RECEIVED("已收货", "完成"),

    /** 已取消（结束过程的落点）：未支付单被取消——顾客主动取消，或支付超时被定时任务自动取消 */
    CANCELLED("已取消", "已取消"),

    /** 已退款（结束过程的落点）：已支付未发货的单被仅退款（一步生效，无需商户同意） */
    REFUNDED("已退款", "已退款");

    /**
     * 已结束：**走到这里就再没有下一步**的三个状态 —— 已收货（主链走到头），
     * 已取消 / 已退款（两个结束过程的落点，它们不在主链上）。
     *
     * <p>⚠ 它们**不参与第二级幂等去重**（{@code OrderRepository#findRecentByFingerprint}）。
     * 去重的语义是「同一顾客在窗口内的重复提交＝同一次购买意图」，而一笔**已经结束**的单不代表任何
     * 还活着的购买意图：复用它会让「取消 / 退款 / 收货后重下同一批商品」拿回那笔**既没重新扣库存、
     * 也付不了款**的旧单（主链上「已收货」之后没有下一步，已取消 / 已退款更是整条主链之外的状态，
     * 它们都回不到「已支付」），而顾客那边看到的是「下单成功」。故判定口径是「窗口内**且仍未结束**的那一笔」。</p>
     *
     * <p>⚠ 唯一一份声明就在这里：内存实现按 {@link #isEnded()} 过滤，落库实现按 {@link #endedNames()}
     * 过滤，两处都从本集合派生。且它与状态机**锁死**——{@link OrderStatusFlow} 在装配期断言
     * 「本集合 == 主链末项 + 两个结束过程的落点」：新加一个状态却忘了在这里登记，
     * 后果是那笔单被当成仍在途的单复用来顶掉新单（静默错），故宁可让服务起不来。</p>
     */
    private static final List<OrderStatus> ENDED = List.of(RECEIVED, CANCELLED, REFUNDED);

    /**
     * {@link #ENDED} 的常量名（落库实现的过滤条件用），**类初始化时算一次**。
     *
     * <p>⚠ 不是为了省那点开销，是为了不让「每次判重都现算一遍」成为一处**每次调用都可能不一样**的东西
     * （{@link #endedNames()} 在下单主链路上每次拆店判重都会调一次）。它与 {@link #ENDED} 同源，
     * 故不存在第二份会漂移的清单。</p>
     */
    private static final List<String> ENDED_NAMES = ENDED.stream().map(OrderStatus::name).toList();

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

    /**
     * 本状态是否已结束（见 {@link #ENDED}）——内存实现的过滤判据
     */
    public boolean isEnded() {
        return ENDED.contains(this);
    }

    /**
     * 全部已结束状态的**常量名**。
     *
     * <p>⚠ 落库实现要的是名字不是枚举：{@code trade_order.status} 存的是 {@link #name()}
     * （见本类注释「状态已落库」那句），过滤只能按字符串比。</p>
     */
    public static List<String> endedNames() {
        return ENDED_NAMES;
    }
}
