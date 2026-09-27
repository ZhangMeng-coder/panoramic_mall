package com.panoramic.storebff.dto;

import com.panoramic.common.stats.StatsWindow;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 店主端「首页数据看板」查询参数（**页面入参**，store-bff 自有）。
 *
 * <p>⚠ <b>没有 {@code storeId} 字段</b>（cross-cutting 第 22 条）：作用域在
 * {@code StoreStatsBffService} 里被<b>无条件</b>写成登录态（店主侧只有「本店」一个视角），
 * 写进的是<b>域侧</b>入参 DTO，不是本类。页面能传来的锚点等于把数据权限交给页面。</p>
 *
 * <p>⚠ <b>窗口枚举只到端 BFF 为止</b>：本层把它解析成显式的 {@code start} / {@code end} 再传给两个域，
 * 域接口<b>不收</b>窗口枚举、<b>不收</b>分桶粒度，且一律按天出点（cross-cutting 第 25 条）。
 * 日历算术与「按天 → 按月」的归并<b>不在本层</b>——用的是 {@code common} 的
 * {@link com.panoramic.common.stats.StatsWindows} / {@link com.panoramic.common.stats.StatsSeriesMerger}，
 * 与平台看板（admin BFF）<b>同一份</b>（同一规则不写两遍）。</p>
 *
 * <p>⚠ <b>三项快照指标不受本对象影响</b>：上架 / 下架商品数与库存异常 SKU 数没有时间维度，
 * 域接口也不收时间参数（第 25 条明确「别为了对称硬塞 {@code start} / {@code end}」）；
 * <b>评价分布按窗口</b>，故它也落在下面这组字段的射程内（判据见 store-bff README 第 4 节）。</p>
 *
 * <p>⚠ 绑定失败即 400：{@code window} 认不出的取值由 common 的 {@code GlobalExceptionHandler}
 * 归成「请求参数取值非法：window」，不在本层另写一份校验文案；{@code start} / {@code end}
 * 的日期格式同理由框架兜底（ISO {@code yyyy-MM-dd}）。</p>
 */
@Data
public class StoreStatsQueryDTO {

    /**
     * 时间窗口；<b>不传 = 本月</b>（页面默认选中的也是本月，缺省值只在
     * {@link com.panoramic.common.stats.StatsWindows#DEFAULT_WINDOW} 一处）
     */
    private StatsWindow window;

    /**
     * 自定义窗口起点（<b>仅 {@link StatsWindow#CUSTOM} 用</b>，且必填）；其余窗口传了会被忽略
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    /**
     * 自定义窗口终点（<b>仅 {@link StatsWindow#CUSTOM} 用</b>，且必填）；<b>含当天整天</b>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
}
