package com.panoramic.common.stats;

/**
 * 折线粒度（页面 x 轴标签的格式化依据）。
 *
 * <p>⚠ <b>由 BFF 算出来、随响应下发给页面</b>，页面**不得**自己按窗口天数重算这条规则——
 * 那会让「≤ 180 天按天、否则按月」有第二处实现（{@link StatsWindows#grainOf}）。
 * 页面拿到哪个就按哪个格式化即可。</p>
 */
public enum StatsGrain {

    /** 窗口跨度 ≤ {@value StatsWindows#DAILY_MAX_SPAN_DAYS} 天：一天一个点 */
    DAY,

    /** 窗口跨度 &gt; {@value StatsWindows#DAILY_MAX_SPAN_DAYS} 天：一个月一个点 */
    MONTH
}
