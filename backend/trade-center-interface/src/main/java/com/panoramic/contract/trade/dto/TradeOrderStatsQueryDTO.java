package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 订单统计查询参数（trade-center 域内部接口与 admin BFF 同源共享）。
 * <p>用途：平台首页数据看板的「总营业额」「成交订单数量」「成交订单比例」「新增订单折线」。
 * ⚠ 本域<b>第一条聚合能力</b>：无作用域锚点，统计的是<b>全平台</b>订单，不按
 * {@code customerId} / {@code storeId} 过滤——<b>不是</b>「作用域可选」，而是本就没有锚点这一维。</p>
 * <p>⚠ <b>只收显式起止、不收「窗口枚举」也不收「粒度」</b>：{@code 本月/上季/去年} 这类日历算术
 * 与日→月归并<b>只在发起调用的端 BFF</b>（admin）里发生，域侧一律按天出点。理由与判据见
 * cross-cutting 第 25 条：窗口解析若下沉到各域，同一套月/季/年算术会在每个域里各存一份而互相漂移。</p>
 * <p>⚠ 与顾客统计（{@code CustomerStatsQueryDTO}）不同，这里<b>两个字段都必填</b>：本接口四个部件
 * <b>全部</b>都是窗口量，不存在「不传窗口也能回的快照部件」——省掉边界就没有可回的东西。</p>
 */
@Data
public class TradeOrderStatsQueryDTO {

    /**
     * 窗口起（含当天）。与 {@link #end} 同日即「只看今天」。
     * <p>⚠ ISO {@code yyyy-MM-dd}（{@code @DateTimeFormat} 显式钉死，不依赖两端默认格式是否一致）。</p>
     */
    @NotNull(message = "请选择统计开始日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    /**
     * 窗口止（<b>含当天整天</b>：营业额与新增折线的上界都取「次日 00:00」并用 {@code <} 排除）。
     */
    @NotNull(message = "请选择统计结束日期")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
}
