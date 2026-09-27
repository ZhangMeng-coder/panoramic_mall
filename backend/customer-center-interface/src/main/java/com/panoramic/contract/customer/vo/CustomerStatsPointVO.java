package com.panoramic.contract.customer.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 顾客统计的<b>单日</b>数据点（customer-center 域内部接口与 admin BFF 同源共享）。
 * <p>⚠ 域侧<b>一律按天出点</b>：更粗的粒度（月/季/年）由调用方（admin BFF）归并，域不接受 {@code grain}
 * 参数——窗口与粒度的规则在整个系统里只有一处，见 cross-cutting 第 25 条。</p>
 * <p>⚠ 出参<b>只含「有数据的日期」</b>（{@code GROUP BY} 的自然结果），<b>缺的日期不补 0</b>：
 * 补 0 到「哪天到哪天」需要知道窗口边界，而窗口是调用方的概念（域只认它收到的 start/end，
 * 调用方本来就知道自己要画几个点）。调用方按「该日不在列表里 = 0」处理。</p>
 */
@Data
public class CustomerStatsPointVO {

    /**
     * 日期（当天 00:00 ~ 次日 00:00 的区间，服务端时区的自然日）
     */
    private LocalDate date;

    /**
     * 该日新增的顾客资料数（恒 ≥ 1：为 0 的日期不会出现在出参里）
     */
    private Long count;
}
