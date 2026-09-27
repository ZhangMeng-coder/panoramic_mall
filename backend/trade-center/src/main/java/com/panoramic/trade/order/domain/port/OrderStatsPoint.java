package com.panoramic.trade.order.domain.port;

import java.time.LocalDate;

/**
 * 订单统计的**单日**数据点（域内形状，出参 VO 由应用层另行翻译）。
 *
 * <p>⚠ 域侧**一律按天出点**：更粗的粒度（月 / 季 / 年）由调用方归并，域不接受粒度参数
 * （见 cross-cutting 第 25 条——窗口与粒度的规则在整个系统里只有一处）。</p>
 *
 * @param date  日期（当天 00:00 ~ 次日 00:00 的自然日）
 * @param count 该日的新增下单数（恒 ≥ 1：为 0 的日期不会出现在结果里）
 */
public record OrderStatsPoint(LocalDate date, long count) {
}
