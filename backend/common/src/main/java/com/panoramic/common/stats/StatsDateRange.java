package com.panoramic.common.stats;

import java.time.LocalDate;

/**
 * 窗口的**闭区间**（两端都含当天）。
 *
 * <p>⚠ 它是**日期**不是时刻：域侧把上界取成 {@code end} 次日 00:00 并用 {@code <} 排除，
 * 故这里给出 {@code end} 当天即可，**不必也不能**给出 {@code 23:59:59}——
 * 收的是日期，转时刻是各域自己的事。</p>
 */
public record StatsDateRange(LocalDate start, LocalDate end) {
}
