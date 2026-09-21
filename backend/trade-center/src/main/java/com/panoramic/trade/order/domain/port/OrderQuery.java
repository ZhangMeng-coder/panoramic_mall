package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderStatus;

/**
 * 订单分页查询条件（**顾客 / 商户侧**：不含锚点，也不含平台侧那两个可选筛选）。
 *
 * <p>⚠ 锚点（顾客 / 店铺）刻意不在这里，而在 {@link OrderRepository} 的分页方法入参上：
 * 平台侧是全量视角、没有锚点，若把它做成可选字段，就会顺理成章地出现「顾客侧传了 storeId」
 * 「平台侧漏传 customerId」这类**形态上本不该成立**的调用。分成三个方法之后，
 * 「哪一侧能筛什么」由方法签名说了算，漏传锚点这件事连编译都过不去。</p>
 *
 * <p>平台侧那两个**可选筛选**（店铺 / 顾客）同理不进本类，它们属 {@link PlatformOrderQuery}
 * ——放这里就等于给顾客侧也开了「按店铺筛」的口子。</p>
 *
 * <p>共同的四个字段与两条取值校验在 {@link OrderPageQuery} 里，本类只声明自己的成分。</p>
 *
 * @param orderNo  订单号精确匹配；可为 null（不筛），空白串按「不筛」处理
 * @param status   订单状态；可为 null（不筛）
 * @param pageNum  页码，从 1 起
 * @param pageSize 每页条数，取值 {@code 1..MAX_PAGE_SIZE}
 */
public record OrderQuery(String orderNo, OrderStatus status, int pageNum, int pageSize)
        implements OrderPageQuery {

    public OrderQuery {
        OrderPageQuery.validatePaging(pageNum, pageSize);
        orderNo = OrderPageQuery.normalizeOrderNo(orderNo);
    }
}
