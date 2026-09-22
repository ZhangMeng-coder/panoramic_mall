package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderStatus;

/**
 * 订单查询条件（**分页与详情共用一份**：订单号 + 状态 + 两个可选作用域 + 分页）。
 *
 * <p>⚠ 作用域（顾客 / 店铺）是**可选字段**，不是「筛选项」：订单读能力有**合法全量视角**
 * （管理端看全量），故「传了就按它筛，没传就是不限定」——见 cross-cutting 第 22 条。
 * 域内因此只有一条读路径（{@link OrderRepository#pageOrders} / {@link OrderRepository#findOrder}），
 * 不再按端分方法：「哪一侧能看什么」由调用方传不传作用域决定，域不判身份。</p>
 *
 * <p>⚠ 与写侧的分野：写操作（支付 / 发货 / 收货）**没有**合法全量视角，其作用域在契约层的
 * 入参 DTO 上必填（{@code @NotNull}），不靠本记录的可选字段兜底。</p>
 *
 * <p>⚠ 详情也走本记录（{@link #forDetail}）：那里只有 {@code orderNo} 与两个作用域有意义，
 * 分页两项是满足公共形状的占位——单开一个只差「有没有页码」的类型，等于把同一份作用域写两遍。</p>
 *
 * @param orderNo    订单号精确匹配；可为 null（不筛），空白串按「不筛」处理
 * @param status     订单状态；可为 null（不筛）
 * @param customerId 顾客 id 作用域；可为 null（不限定）
 * @param storeId    店铺 id 作用域；可为 null（不限定）
 * @param pageNum    页码，从 1 起（详情查询不使用，见 {@link #forDetail}）
 * @param pageSize   每页条数，取值 {@code 1..MAX_PAGE_SIZE}（详情查询不使用）
 */
public record OrderQuery(String orderNo, OrderStatus status, Long customerId, Long storeId,
                         int pageNum, int pageSize) implements OrderPageQuery {

    public OrderQuery {
        OrderPageQuery.validatePaging(pageNum, pageSize);
        orderNo = OrderPageQuery.normalizeOrderNo(orderNo);
    }

    /**
     * 详情查询条件：只有订单号与两个作用域有意义，分页两项取 {@code 1/1} 占位（不翻页）
     *
     * <p>⚠ 用命名工厂而不是让调用方自己拼 {@code 1, 1}：那两个数在详情里没有含义，
     * 摆在调用处会读成「查第一页第一条」——一个不存在的语义。</p>
     *
     * @param orderNo    业务可读单号（必填）
     * @param customerId 顾客 id 作用域；可为 null（不限定）
     * @param storeId    店铺 id 作用域；可为 null（不限定）
     * @return 详情查询条件
     */
    public static OrderQuery forDetail(String orderNo, Long customerId, Long storeId) {
        return new OrderQuery(orderNo, null, customerId, storeId, 1, 1);
    }
}
