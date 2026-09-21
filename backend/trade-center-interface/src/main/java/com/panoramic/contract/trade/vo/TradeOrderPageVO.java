package com.panoramic.contract.trade.vo;

import lombok.Data;

import java.util.List;

/**
 * 订单分页响应（trade-center 域内部接口与端 BFF 同源共享）。
 *
 * <p>⚠ <b>刻意叫 {@code TradeOrderPageVO} 而不叫 {@code PageResult}</b>：本仓库已有三份同形同名的
 * {@code PageResult}（{@code contract.goods.vo} / {@code contract.store.vo} / admin 本地一份），
 * 再加第四份只会把「别引错包」那份清单继续撑大（见 {@code docs/contracts/cross-cutting.md} 第 3 条）。
 * 各端 BFF 收到本 VO 后**自行映射**成自己那份 {@code PageResult<...>}。</p>
 */
@Data
public class TradeOrderPageVO {

    /**
     * 满足条件的总条数（不受分页限制）
     */
    private Long total;

    /**
     * 当页订单（每笔都**带齐明细**，由域内批量补齐，不做 N+1）；本页无数据为空列表，不是 null
     */
    private List<TradeOrderVO> records;
}
