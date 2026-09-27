package com.panoramic.contract.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单统计响应（trade-center 域内部接口与 admin BFF 同源共享）。
 * <p><b>四个部件、时间基准刻意不同，别当成不一致去「修」</b>：{@code revenue} / {@code dealOrderCount} /
 * {@code paidOrderCount} 按<b>支付时间</b>归属（只数「窗口内发生过支付」的那批单），
 * {@code newSeries} 按<b>下单时间</b>（{@code create_time}）分桶——「钱动了多少」与「新下了多少单」
 * 本就是两个口径。</p>
 * <p>⚠ <b>比率不在这里</b>：域只回分子分母两个计数，比率与除零由 admin BFF 处理
 * （比率是展示层的派生量，域不产出「已经除过的数」）。</p>
 * <p>⚠ {@code revenue} 可能是 {@code 0}，但<b>不会是 null</b>：「这个窗口没赚到钱」是有效结果。</p>
 */
@Data
public class TradeOrderStatsVO {

    /**
     * 窗口内营业额：按<b>支付时间</b>落在窗口内的订单，其总额之和；
     * ⚠ 已退款（{@code REFUNDED}）的<b>整单扣减</b>（本仓退款恒为全额，没有部分退款口径）。
     */
    private BigDecimal revenue;

    /**
     * 窗口内成交订单数（**分子**）：按支付时间落在窗口内、且<b>当前已完成</b>（{@code RECEIVED}）的订单数。
     */
    private Long dealOrderCount;

    /**
     * 窗口内已支付过的订单数（**分母**）：按支付时间落在窗口内的订单数，
     * 含后来被退款的那部分——「成交比例」的分母是「付过款的」，不是「还留着的」。
     */
    private Long paidOrderCount;

    /**
     * 窗口内每日新增下单数（按 {@code create_time} 落日；含后来被取消 / 退款的单——
     * 它数的是「下了多少单」，不是「留下了多少单」）。
     * <p>只含有数据的日期，缺日由调用方按 0 处理，不返回 null。</p>
     */
    private List<TradeOrderStatsPointVO> newSeries;
}
