package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderStatus;

/**
 * 订单分页查询条件的**公共形状**：订单号 / 状态 / 页码 / 页大小 + 两处共同校验。
 *
 * <p>⚠ 它存在的唯一理由是「**三侧共用的那四个字段与它们的两条校验只写一份**」：
 * {@link OrderQuery}（顾客 / 商户侧）与 {@link PlatformOrderQuery}（平台侧）都实现它，
 * 仓储的分页方法按本接口取值；而「哪一侧另有哪些筛选字段」仍旧由**各自的 record** 决定
 * ——锚点（顾客 / 店铺）留在 {@link OrderRepository} 的分页方法入参上，
 * 平台侧特有的两个**可选筛选**（店铺 / 顾客）留在 {@link PlatformOrderQuery} 里。</p>
 *
 * <p>⚠ 不要把锚点混进来：平台侧是全量视角，锚点做成可选字段就会顺理成章地出现
 * 「顾客侧传了 storeId」「平台侧漏传 customerId」这类**形态上本不该成立**的调用；
 * 分成三个方法之后，「哪一侧能筛什么」由方法签名说了算（理由见 {@link OrderQuery} 的类注释）。</p>
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

    /** @return 页码，从 1 起 */
    int pageNum();

    /** @return 每页条数，取值 {@code 1..MAX_PAGE_SIZE} */
    int pageSize();

    /** 各实现类的紧凑构造器都调它，避免三条分页路径各判一套 */
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
