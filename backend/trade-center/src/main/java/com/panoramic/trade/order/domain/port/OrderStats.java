package com.panoramic.trade.order.domain.port;

import java.math.BigDecimal;
import java.util.List;

/**
 * 平台订单统计（首页看板用；域内形状，出参 VO 由应用层另行翻译）。
 *
 * <p><b>四个部件，时间基准刻意不同</b>（别当成不一致去「修」）：</p>
 * <ul>
 *   <li>{@code revenue} / {@code dealOrderCount} / {@code paidOrderCount} —— 按<b>支付时间</b>归属：
 *       都只数「窗口内发生过支付」的那批单；</li>
 *   <li>{@code newSeries} —— 按<b>下单时间</b>分桶：「新下了多少单」与「钱动了多少」本就是两个口径。</li>
 * </ul>
 *
 * <p>⚠ <b>比率不在这里</b>：本记录只带分子（{@code dealOrderCount}）与分母（{@code paidOrderCount}），
 * 比率与除零由调用方（admin BFF）处理——比率是展示层的派生量，域不产出「已经除过的数」。</p>
 *
 * <p>⚠ <b>营业额可能为 0，但不会是 null</b>：窗口内没有已支付订单时是 {@code 0}（不是「没有数据」），
 * 因为「这个窗口没赚到钱」本身就是一个有效结果。</p>
 *
 * @param revenue        窗口内营业额（按支付时间归属；已退款整单扣减，口径见 trade-center README）
 * @param dealOrderCount 窗口内的成交订单数（分子）
 * @param paidOrderCount 窗口内已支付过的订单数（分母）
 * @param newSeries      窗口内每日新增下单数（按 {@code create_time} 落日；只含有数据的日期，缺日即 0）
 */
public record OrderStats(BigDecimal revenue,
                         long dealOrderCount,
                         long paidOrderCount,
                         List<OrderStatsPoint> newSeries) {
}
