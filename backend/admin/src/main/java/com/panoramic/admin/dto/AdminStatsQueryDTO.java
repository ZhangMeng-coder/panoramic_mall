package com.panoramic.admin.dto;

import com.panoramic.common.stats.StatsWindow;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理后台「首页数据看板」查询参数（**页面入参**，admin BFF 自有）。
 *
 * <p>⚠ <b>窗口枚举只到端 BFF 为止</b>：本层把它解析成显式的 {@code start} / {@code end} 再传给三个域，
 * 域接口**不收**窗口枚举、**不收**分桶粒度，且一律按天出点（cross-cutting 第 25 条）。
 * 「本月 / 本季 / 今年」的日历算术与「按天 → 按月」的归并**不在本层**——
 * 2026-09-27 起上收到 {@code common} 的 {@link com.panoramic.common.stats.StatsWindows} /
 * {@link com.panoramic.common.stats.StatsSeriesMerger}，与店主端看板共用同一份（同一规则不写两遍）。</p>
 *
 * <p>⚠ 三项快照指标（用户数 / 商家数 / 商品数）**不受本对象影响**：它们没有时间维度，
 * 域接口也不收时间参数（第 25 条明确「别为了对称硬塞 {@code start} / {@code end}」）。</p>
 *
 * <p>⚠ 绑定失败即 400：{@code window} 认不出的取值由 common 的
 * {@code GlobalExceptionHandler} 归成「请求参数取值非法：window」，不在本层另写一份校验文案。
 * {@code start} / {@code end} 的日期格式同理由框架兜底（ISO {@code yyyy-MM-dd}）。</p>
 */
@Data
public class AdminStatsQueryDTO {

    /**
     * 时间窗口；**不传 = 本月**（页面默认选中的也是本月，缺省值在两端同口径，
     * 落在 {@link com.panoramic.common.stats.StatsWindows#DEFAULT_WINDOW}）
     */
    private StatsWindow window;

    /**
     * 自定义窗口起点（**仅 {@link StatsWindow#CUSTOM} 用**，且必填）；其余窗口传了会被忽略
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    /**
     * 自定义窗口终点（**仅 {@link StatsWindow#CUSTOM} 用**，且必填）；**含当天整天**
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
}
