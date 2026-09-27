package com.panoramic.common.stats;

import com.panoramic.common.exception.ServiceException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 时间窗口 → 显式起止，以及窗口 → 折线粒度。**全系统唯一一处**（cross-cutting 第 25 条）。
 *
 * <p>⚠ 整套月 / 季 / 年算术与「≤ {@value #DAILY_MAX_SPAN_DAYS} 天按天」这条分界线都在本类，
 * 端 BFF 只调不算——复制第二份出来就是造第二个会漂移的地方。</p>
 *
 * <p>⚠ 无 Spring 依赖（不注入 `Clock`）：{@code today} 由调用方传入，页面默认与调用方同口径，
 * 也让本类可以脱离容器直接单测。</p>
 */
public final class StatsWindows {

    /**
     * 折线粒度的分界线（**含**）：窗口跨度 ≤ 本值按天出点，&gt; 本值按月归并。
     *
     * <p>⚠ 180 而不是更小的数：本季 / 上季最长 92 天，按天出点正好放得下（≤ 92 个点），
     * 而「今年 / 去年」必须按月——否则 365 个点在图上糊成一片。取 62 会让本季退化成 3 个点，太粗。</p>
     */
    public static final int DAILY_MAX_SPAN_DAYS = 180;

    /**
     * 窗口缺省值（页面入参不传时用它）。
     *
     * <p>⚠ 页面默认选中的也是这个值，**两端同口径**——改这里就等于改「打开看板看到的是哪一段」。</p>
     */
    public static final StatsWindow DEFAULT_WINDOW = StatsWindow.THIS_MONTH;

    private StatsWindows() {
    }

    /**
     * 窗口 + 自定义起止 → 闭区间 {@code [start, end]}。
     *
     * <p>⚠ 非 {@link StatsWindow#CUSTOM} 时 {@code customStart} / {@code customEnd} **被忽略**
     * （页面若把上次自定义的值一并传了，不该污染枚举窗口）。</p>
     *
     * @param window      窗口；<b>{@code null} 按 {@link #DEFAULT_WINDOW}</b>
     * @param customStart 自定义窗口起点（仅 CUSTOM 用）
     * @param customEnd   自定义窗口终点（仅 CUSTOM 用，**含当天整天**）
     * @param today       「今天」，由调用方按**服务端**时钟取（窗口按自然日历边界算，与浏览器时区无关）
     * @throws ServiceException 自定义窗口缺一端、或结束早于开始（回 400，文案可直接展示）
     */
    public static StatsDateRange resolve(StatsWindow window, LocalDate customStart, LocalDate customEnd,
                                        LocalDate today) {
        StatsWindow effective = window == null ? DEFAULT_WINDOW : window;
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        return switch (effective) {
            case THIS_MONTH -> new StatsDateRange(thisMonthStart, thisMonthStart.plusMonths(1).minusDays(1));
            case LAST_MONTH -> lastMonthOf(thisMonthStart);
            case THIS_QUARTER -> quarterOf(today);
            // 上季末月最后一天 = 本季首日前一天；起点再往前推 3 个月
            case LAST_QUARTER -> {
                LocalDate quarterStart = quarterOf(today).start();
                yield new StatsDateRange(quarterStart.minusMonths(3), quarterStart.minusDays(1));
            }
            case THIS_YEAR -> new StatsDateRange(LocalDate.of(today.getYear(), 1, 1),
                    LocalDate.of(today.getYear(), 12, 31));
            case LAST_YEAR -> new StatsDateRange(LocalDate.of(today.getYear() - 1, 1, 1),
                    LocalDate.of(today.getYear() - 1, 12, 31));
            case CUSTOM -> customRange(customStart, customEnd);
        };
    }

    /** 跨度 ≤ {@value #DAILY_MAX_SPAN_DAYS} 天按天，否则按月（闭区间，两端都算） */
    public static StatsGrain grainOf(StatsDateRange range) {
        long spanDays = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
        return spanDays <= DAILY_MAX_SPAN_DAYS ? StatsGrain.DAY : StatsGrain.MONTH;
    }

    /**
     * 自定义窗口：起止**都必填**、且不得倒挂。
     *
     * <p>⚠ 页面 DTO 的 {@code start} / {@code end} 是**可空**的（只有自定义窗口才用它们），
     * 故必填性只能在这里判——这与「写侧作用域必填由 DTO 上的 {@code @NotNull} 守」的做法不同，
     * 因为那两个字段在别的窗口下本来就该缺省。</p>
     */
    private static StatsDateRange customRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ServiceException("自定义时间窗口必须同时给出起止日期");
        }
        if (end.isBefore(start)) {
            throw new ServiceException("时间窗口不合法：结束日期早于开始日期");
        }
        return new StatsDateRange(start, end);
    }

    /** 上月：以「本月 1 号」为锚往回推，跨年由 {@code minusMonths} 自己处理 */
    private static StatsDateRange lastMonthOf(LocalDate thisMonthStart) {
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        return new StatsDateRange(lastMonthStart, lastMonthStart.plusMonths(1).minusDays(1));
    }

    /** 本季：首月 = {@code (month - 1) / 3 * 3 + 1}（1 / 4 / 7 / 10），末月最后一天 = 首日 + 3 个月 - 1 天 */
    private static StatsDateRange quarterOf(LocalDate today) {
        int firstMonthOfQuarter = (today.getMonthValue() - 1) / 3 * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), firstMonthOfQuarter, 1);
        return new StatsDateRange(start, start.plusMonths(3).minusDays(1));
    }
}
