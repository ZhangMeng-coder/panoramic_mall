package com.panoramic.trade.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单状态枚举的两侧文案（裁定 D7：常量名即 todo 说的 name，另两个字段是 mall / storeAdmin）。
 *
 * <p>文案是**用户可见**的最终输出（域侧拼进异常提示、端侧直接渲染），故逐条钉死：
 * 「同一状态、两个视角」这个设计只在这种断言下才不会被「顺手统一文案」改坏。</p>
 */
class OrderStatusTest {

    @Test
    @DisplayName("四个常量的常量名即 todo 说的 name 字段")
    void constantNamesAreTheNameField() {
        assertThat(Arrays.stream(OrderStatus.values()).map(Enum::name))
                .containsExactly("PENDING_PAYMENT", "PAID", "SHIPPED", "RECEIVED");
    }

    @Test
    @DisplayName("PENDING_PAYMENT：两侧都是「待支付」（此刻顾客与店主的说法一致）")
    void pendingPaymentLabels() {
        assertThat(OrderStatus.PENDING_PAYMENT.getMallLabel()).isEqualTo("待支付");
        assertThat(OrderStatus.PENDING_PAYMENT.getStoreAdminLabel()).isEqualTo("待支付");
    }

    @Test
    @DisplayName("PAID：顾客看到「已支付」、店主看到「待发货」——同一刻的两个视角，不得统一")
    void paidLabelsDifferBySide() {
        assertThat(OrderStatus.PAID.getMallLabel()).isEqualTo("已支付");
        assertThat(OrderStatus.PAID.getStoreAdminLabel()).isEqualTo("待发货");
    }

    @Test
    @DisplayName("SHIPPED：顾客看到「待收货」、店主看到「已发货」")
    void shippedLabelsDifferBySide() {
        assertThat(OrderStatus.SHIPPED.getMallLabel()).isEqualTo("已发货");
        assertThat(OrderStatus.SHIPPED.getStoreAdminLabel()).isEqualTo("待收货");
    }

    @Test
    @DisplayName("RECEIVED：顾客看到「已收货」、店主侧口径是「完成」")
    void receivedLabels() {
        assertThat(OrderStatus.RECEIVED.getMallLabel()).isEqualTo("已收货");
        assertThat(OrderStatus.RECEIVED.getStoreAdminLabel()).isEqualTo("完成");
    }

    @Test
    @DisplayName("每个常量的两侧文案都非空且非空白（文案会被直接渲染给用户）")
    void everyLabelIsRenderable() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.getMallLabel()).as("%s 的商城端文案", status.name()).isNotBlank();
            assertThat(status.getStoreAdminLabel()).as("%s 的商户端文案", status.name()).isNotBlank();
        }
    }
}
