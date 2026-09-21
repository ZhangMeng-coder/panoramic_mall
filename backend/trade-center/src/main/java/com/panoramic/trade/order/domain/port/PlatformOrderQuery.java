package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderStatus;

/**
 * 订单分页查询条件（**平台侧**：全量视角，另带两个可选筛选）。
 *
 * <p>⚠ 为什么另开一个类而不是给 {@link OrderQuery} 加两个可空字段：加了之后
 * 「顾客 / 商户侧按店铺筛」在**类型上**就成立了，于是「锚点与筛选混用」的调用迟早会出现
 * ——那类调用不会报错，只会静默返回别的店铺 / 别人的订单。分成两个类之后，
 * 平台侧能筛什么由类型说了算。</p>
 *
 * <p>⚠ 平台侧**没有锚点**：{@code storeId} / {@code customerId} 在这里是「管理员想按店铺 / 顾客过滤」
 * 的**可选条件**，与顾客 / 商户侧那个「必须命中的数据权限锚点」是两回事——
 * 后者在 {@link OrderRepository} 的方法入参上，前者在本记录里。</p>
 *
 * <p>四个共同字段与两条取值校验在 {@link OrderPageQuery} 里，本类只加自己的两个成分。</p>
 *
 * @param orderNo    订单号精确匹配；可为 null（不筛），空白串按「不筛」处理
 * @param status     订单状态；可为 null（不筛）
 * @param storeId    店铺 id；可为 null（不筛。这是**筛选**，不是锚点）
 * @param customerId 顾客 id；可为 null（不筛。这是**筛选**，不是锚点）
 * @param pageNum    页码，从 1 起
 * @param pageSize   每页条数，取值 {@code 1..MAX_PAGE_SIZE}
 */
public record PlatformOrderQuery(String orderNo, OrderStatus status, Long storeId, Long customerId,
                                 int pageNum, int pageSize) implements OrderPageQuery {

    public PlatformOrderQuery {
        OrderPageQuery.validatePaging(pageNum, pageSize);
        orderNo = OrderPageQuery.normalizeOrderNo(orderNo);
    }
}
