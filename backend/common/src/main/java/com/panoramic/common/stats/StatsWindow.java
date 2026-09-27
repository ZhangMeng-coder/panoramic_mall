package com.panoramic.common.stats;

/**
 * 时间窗口取值（页面的下拉框选项，1:1）。
 *
 * <p>⚠ 全部按<b>自然日历边界</b>（本月 = 当月 1 号至月末，**不是**「最近 30 天」那一类滑动窗口）。</p>
 *
 * <p>⚠ <b>本季 / 上季不做降级</b>：季度边界在 {@code LocalDate} 上是
 * {@code (month - 1) / 3 * 3 + 1} 的几行算术，不需要引库、也不需要退化成「最近 3 月」。</p>
 *
 * <p>⚠ 使用者是**端 BFF 的页面入参 DTO**；域接口不得引用本类型（见包注释）。</p>
 */
public enum StatsWindow {

    /** 本月 1 号 → 本月最后一天 */
    THIS_MONTH,

    /** 上月 1 号 → 上月最后一天 */
    LAST_MONTH,

    /** 本季首月 1 号 → 本季末月最后一天 */
    THIS_QUARTER,

    /** 上季首月 1 号 → 上季末月最后一天 */
    LAST_QUARTER,

    /** 本年 1 月 1 号 → 12 月 31 号 */
    THIS_YEAR,

    /** 去年 1 月 1 号 → 12 月 31 号 */
    LAST_YEAR,

    /** 自定义起止（用页面 DTO 的 {@code start} / {@code end}，两者**必填**） */
    CUSTOM
}
