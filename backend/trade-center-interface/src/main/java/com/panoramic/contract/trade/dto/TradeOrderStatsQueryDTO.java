package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 订单统计查询参数（trade-center 域内部接口与两端 BFF 同源共享）。
 * <p>用途：<b>平台</b>首页看板的「总营业额 / 成交订单数量 / 成交订单比例 / 新增订单折线」与
 * <b>店主端</b>首页看板的同名四项（后者多传一个 {@code storeId}）。</p>
 * <p>⚠ <b>作用域 {@code storeId} 可选</b>（2026-09-27 起）：不传 = 全平台（admin BFF），
 * 传了 = 本店（store-bff）——与读侧 {@code TradeOrderPageQueryDTO} 的 {@code customerId} / {@code storeId}
 * 同为「传了就按它筛，没传就是不限定」。⚠ 此前本接口 javadoc 写的是「本就没有锚点这一维」，
 * 那是**当时**的事实（只有 admin 在调），现已订正：**有**这一维，只是可省。</p>
 * <p>⚠ {@code customerId} <b>不收</b>：顾客端没有订单独占的统计看板，加了也没有调用方
 * （与 {@code TradeOrderPageQueryDTO} 收它是两回事——那边是「我的订单」列表）。</p>
 * <p>⚠ <b>只收显式起止、不收「窗口枚举」也不收「粒度」</b>：{@code 本月/上季/去年} 这类日历算术
 * 与日→月归并<b>只在发起调用的端 BFF</b> 里发生，域侧一律按天出点。理由与判据见
 * cross-cutting 第 25 条：窗口解析若下沉到各域，同一套月/季/年算术会在每个域里各存一份而互相漂移。</p>
 * <p>⚠ 与顾客统计（{@code CustomerStatsQueryDTO}）不同，这里 <b>{@code start} / {@code end} 都必填</b>：
 * 本接口四个部件<b>全部</b>都是窗口量，不存在「不传窗口也能回的快照部件」——省掉边界就没有可回的东西。</p>
 */
@Data
public class TradeOrderStatsQueryDTO {

    /**
     * 作用域：店铺 id（= 店主账号 id）；空 = 不限定店铺（全平台）。
     *
     * <p>⚠ <b>值只能由端 BFF 从登录态取</b>（store-bff 传 {@code LoginUser.getId()}），
     * <b>禁止</b>从前端入参透传（cross-cutting 第 22 条）；域内只做「传了就按它筛」，
     * 不校验「是否真是本店」——防线在 BFF。</p>
     */
    private Long storeId;

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
