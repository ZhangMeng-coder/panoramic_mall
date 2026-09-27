package com.panoramic.admin.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 管理后台「首页数据看板」查询参数（**页面入参**，admin BFF 自有）。
 *
 * <p>⚠ <b>窗口枚举只到本层为止</b>：本层把它解析成显式的 {@code start} / {@code end} 再传给三个域，
 * 域接口**不收**窗口枚举、**不收**分桶粒度，且一律按天出点（cross-cutting 第 25 条）。
 * 「本月 / 本季 / 今年」的日历算术与「按天 → 按月」的归并**只在 {@code AdminStatsBffService} 一处**——
 * 沉到域里就会在每个域各存一份、各自漂移。</p>
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
     * 时间窗口；**不传 = 本月**（页面默认选中的也是本月，缺省值在两端同口径）
     */
    private Window window;

    /**
     * 自定义窗口起点（**仅 {@link Window#CUSTOM} 用**，且必填）；其余窗口由本层算，传了会被忽略
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    /**
     * 自定义窗口终点（**仅 {@link Window#CUSTOM} 用**，且必填）；**含当天整天**
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;

    /**
     * 时间窗口取值（页面的下拉框选项，1:1）。
     *
     * <p>⚠ 全部按**自然日历边界**（本月 = 当月 1 号至月末，不是「最近 30 天」），
     * 不是「最近 N 天」那一类滑动窗口。</p>
     *
     * <p>⚠ <b>本季 / 上季不做降级</b>：季度边界在 {@code LocalDate} 上是
     * {@code (month - 1) / 3 * 3 + 1} 的几行算术，不需要引库、也不需要退化成「最近 3 月」。</p>
     */
    public enum Window {

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

        /** 自定义起止（用 {@link #start} / {@link #end}，两者必填） */
        CUSTOM
    }
}
