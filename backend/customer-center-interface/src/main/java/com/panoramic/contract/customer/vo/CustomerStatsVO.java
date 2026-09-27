package com.panoramic.contract.customer.vo;

import lombok.Data;

import java.util.List;

/**
 * 顾客统计响应（customer-center 域内部接口与 admin BFF 同源共享）。
 * <p><b>两个部件、时间基准不同，刻意共存于一个接口</b>（对照 store 域 {@code getShopStats} 的双计数同因：
 * 它们同属「顾客规模」这一件事，拆成两个接口只会让调用方多跑一次跨服务往返）：</p>
 * <ul>
 *   <li>{@code totalCount} —— <b>当前累计快照</b>，<b>不受入参影响</b>（没传窗口也照样有值）；</li>
 *   <li>{@code newSeries} —— <b>窗口内</b>按 {@code create_time} 分桶的<b>每日</b>新增，窗口外的日子不出现。</li>
 * </ul>
 * <p>⚠ <b>口径是近似，不是恒等</b>：本数取自 {@code customer_profile} 行数，<b>不等于</b> {@code mall_user}
 * （注册账号）行数——偏差的两个方向与成因见 {@code backend/customer-center/README.md}。
 * 故<b>不得</b>把 {@code totalCount} 对外表述为「注册用户数」。</p>
 */
@Data
public class CustomerStatsVO {

    /**
     * 顾客资料总数（当前累计快照）。
     * <p>⚠ 近似值，非「注册用户数」——见类注释。</p>
     */
    private Long totalCount;

    /**
     * 窗口内每日新增（按 {@code create_time} 落日；只含有数据的日期，缺日即 0）。
     * <p>入参未给窗口时为空列表，不返回 null。</p>
     */
    private List<CustomerStatsPointVO> newSeries;
}
