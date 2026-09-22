package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderStatus;

/**
 * 订单分页查询条件的**公共形状**：订单号 / 状态 / **两个可选作用域** / 页码 / 页大小 + 两处共同校验。
 *
 * <p>⚠ 作用域（顾客 / 店铺）在这里是**可选字段**，与契约层同形：订单分页是**有合法全量视角**的能力
 * （管理端看全量），故「传了就按它筛，没传就是不限定」——见 cross-cutting 第 22 条。域内的分页
 * 因此只有**一条路径**（{@link OrderRepository#pageOrders}），不再是「顾客 / 商户 / 平台各一条」：
 * 域不判身份、不分端，同一份条件对任何调用方都一样。</p>
 *
 * <p>⚠ 不要把这两个字段当「筛选项」用错地方：写操作（支付 / 发货 / 收货）**没有**合法全量视角，
 * 它们的作用域在各自的入参 DTO 上**必填**，不走本接口。</p>
 *
 * <p>页码与页大小在这里做一次校验（{@link IllegalArgumentException} 属编程错误）：
 * 契约层已有 {@code @Max(100)} 之类的约束，但那是对**页面入参**的约束；
 * 到达仓储的取值必须自洽，否则 {@code LIMIT -1} 这类越界值会变成一条语义不明的 SQL。</p>
 */
public interface OrderPageQuery {

    /** 单页上限：与契约层分页入参同口径（本仓库 {@code BasePageVO} 的 {@code @Max(100)}） */
    int MAX_PAGE_SIZE = 100;

    /** @return 订单号精确匹配；{@code null} = 不筛 */
    String orderNo();

    /** @return 订单状态；{@code null} = 不筛 */
    OrderStatus status();

    /** @return 顾客 id 作用域；{@code null} = 不限定（管理端全量视角） */
    Long customerId();

    /** @return 店铺 id 作用域；{@code null} = 不限定（管理端全量视角） */
    Long storeId();

    /** @return 页码，从 1 起 */
    int pageNum();

    /** @return 每页条数，取值 {@code 1..MAX_PAGE_SIZE} */
    int pageSize();

    /** 各实现类的紧凑构造器都调它，避免多条分页路径各判一套 */
    static void validatePaging(int pageNum, int pageSize) {
        if (pageNum < 1) {
            throw new IllegalArgumentException("页码必须从 1 起，实际为 " + pageNum);
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页条数必须在 1.." + MAX_PAGE_SIZE + " 之间，实际为 " + pageSize);
        }
    }

    /**
     * 空白串归成 {@code null}
     *
     * <p>调用方把「没填」传成空串是常见写法，把它当成精确匹配空单号只会得空集。</p>
     *
     * @return 去空白后仍有内容则原样返回，否则 {@code null}
     */
    static String normalizeOrderNo(String orderNo) {
        return orderNo == null || orderNo.isBlank() ? null : orderNo;
    }
}
