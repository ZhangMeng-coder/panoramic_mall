package com.panoramic.trade.order.domain;

import java.util.Objects;

/**
 * 建单入参的订单行：**只有 id 与数量**，商品快照与价格都由后续步骤补全（两段式生命周期的第一段）。
 *
 * <p>⚠ 它与 application 层的 {@code OrderCreateCommand.Line} 是**两个类型**，刻意不合并：
 * domain 层零 Spring、不引 application 层类型（裁定 D11），否则依赖方向会从 domain 指回 application，
 * 聚合根就成了应用层的附属品。两者字段相同，属可接受的重复——**类型归属**比省一个 record 重要。</p>
 *
 * <p>⚠ 只在此处校验 {@code skuId} 非空：数量的 {@code 1..999} 口径由 {@link OrderItem#open} 承担
 * （真正的落点是订单项），这里再判一遍只是让错误栈更靠前，不构成第二个口径来源。</p>
 *
 * @param skuId    店铺 SKU id（跨域 id 引用，无外键；域内不校验它是否真实存在）
 * @param quantity 购买数量，取值 {@code 1..999}
 */
public record OrderLine(Long skuId, int quantity) {

    public OrderLine {
        Objects.requireNonNull(skuId, "订单行的 skuId 不能为空");
    }
}
