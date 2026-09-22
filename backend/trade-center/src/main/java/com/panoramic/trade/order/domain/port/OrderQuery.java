package com.panoramic.trade.order.domain.port;

import com.panoramic.common.exception.ServiceException;
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
     * <p>⚠ <b>单号必须非空</b>：空白单号归一成 {@code null} 后，条件里就没有「按单号取」这一项了，
     * 而作用域是可选的——退化成「取任意一笔」（多行时 {@code getOne} 直接抛异常）。详情是资源查询，
     * 没有单号就不是一次合法的查询，故在入口处直接 400——但这句只是**早失败**，
     * 真正的防线在 {@link #requireOrderNo()}（各实现的 {@code findOrder} 必经）。</p>
     *
     * <p>⚠ <b>为什么这里的两个相邻 {@code Long} 不违反第 23 条</b>：第 23 条禁的是「按位置约定区分的裸参」
     * （如 {@code (skuId, spuId)} 传错即写错数据）。此处不成立，理由有两条：① 两个参数是**同一事物的
     * 两种取值**（数据作用域），彼此对称；② **目标行已由 {@code orderNo} 唯一确定**，换序只会让这两个
     * AND 条件匹配不到（→ 404），**不可能写到另一笔单上**。⚠ 别照此写法推广：「两列不同语义、传错即写错目标」
     * 的相邻同类参数仍然必须并进 DTO。</p>
     *
     * @param orderNo    业务可读单号（**必填**，空白即 400）
     * @param customerId 顾客 id 作用域；可为 null（不限定）
     * @param storeId    店铺 id 作用域；可为 null（不限定）
     * @return 详情查询条件
     * @throws ServiceException 单号为空或全是空白（HTTP 400）
     */
    public static OrderQuery forDetail(String orderNo, Long customerId, Long storeId) {
        OrderQuery query = new OrderQuery(orderNo, null, customerId, storeId, 1, 1);
        query.requireOrderNo();
        return query;
    }

    /**
     * 详情查询的**入参不变量**：单号必填（空白串已在紧凑构造器里归一成 {@code null}）
     *
     * <p>⚠ 为什么这条要由**各实现的 {@code findOrder}** 调用，而不是只守在 {@link #forDetail} 里：
     * 本记录是 {@code public}，{@code new OrderQuery(null, PAID, 7L, null, 1, 1)} 是合法代码，
     * 而 {@code forDetail} 只是其中一个工厂——绕过它就能拿到「没有单号条件」的查询，
     * 于是「按单号取一笔」退化成「取作用域内的任意一笔」（多行时 {@code getOne} 还会直接抛异常 → 500）。
     * 详情只有两条实现，{@code findOrder} 是**任何调用方都必经**的地方，故那里才是防线所在；
     * 工厂里那句保留作早失败。</p>
     *
     * @throws ServiceException 单号为空（HTTP 400）
     */
    public void requireOrderNo() {
        if (orderNo == null) {
            throw new ServiceException(400, "订单号不能为空");
        }
    }
}
