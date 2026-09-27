package com.panoramic.common.stats;

import lombok.Data;

import java.time.LocalDate;

/**
 * 看板折线图的一个数据点（新增用户 / 新增订单等折线共用本形状）。
 *
 * <p>⚠ <b>它不是域契约类型</b>：形状与域侧的 {@code CustomerStatsPointVO} /
 * {@code TradeOrderStatsPointVO} 一样，但那两个是「域按天出的**原始**点」，本类承载的是
 * **按窗口补零并归并之后**的点，**两者不可互相替换**（域不知道窗口边界，补零只能在 BFF 做，
 * 见 {@link StatsSeriesMerger}）。</p>
 *
 * <p>⚠ {@code date} 的语义随 {@link StatsGrain} 变：按天出点时是那一天，
 * 按月出点时是**那个月的 1 号**（月桶的标识，不是「这一天有数据」）。</p>
 */
@Data
public class StatsPointVO {

    /**
     * 桶标识（按天 = 当天；按月 = 当月 1 号）
     */
    private LocalDate date;

    /**
     * 该桶的计数。⚠ 窗口内**没有数据的桶是 0**（不是缺项）——零填充在 BFF 做，
     * 因为「窗口从哪天到哪天」只有 BFF 知道（域只回有数据的日期）
     */
    private Long count;
}
