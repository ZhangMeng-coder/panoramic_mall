package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderModel;

import java.util.List;

/**
 * 订单分页结果：**本域自己的一份**，不复用任何一端的 {@code PageResult}。
 *
 * <p>⚠ 本仓库已有三份同形同名的 {@code PageResult}（goods / store / admin 各一份），
 * 再加第四份只会把「别引错包」那份清单继续撑大（见 cross-cutting 第 3 条与 trade-center.md 第三节）。
 * 域内只回「总数 + 当页订单」，各端 BFF 收到后自行映射成自己那份。</p>
 *
 * @param total   满足条件的总条数（不受分页限制）
 * @param records 当页订单（按主键倒序 = 下单倒序；每笔都**带齐订单行**，由实现侧批量补齐，不做 N+1）
 */
public record OrderPage(long total, List<OrderModel> records) {

    public OrderPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
