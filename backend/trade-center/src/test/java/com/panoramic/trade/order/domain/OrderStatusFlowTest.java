package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 状态机：配置覆盖全部常量才装配得起来、只允许「下标 +1」、未 seal 的订单不能迁移。
 *
 * <p>⚠ 「配置错」与「迁移非法」用两种异常（裁定 D12）：前者是程序员错误（{@code IllegalStateException}，
 * 服务起不来最好），后者是用户可见的业务错（{@code ServiceException(400)}，提示直接给页面看）。</p>
 */
class OrderStatusFlowTest {

    private static final List<OrderStatus> FULL_FLOW =
            List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.RECEIVED);

    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private static OrderStatusFlow flow() {
        return new OrderStatusFlow(FULL_FLOW);
    }

    private static OrderModel sealedOrder() {
        OrderModel model = OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-1", "fp-1", LocalDateTime.of(2026, 9, 21, 12, 0, 0), List.of(new OrderLine(10L, 1)));
        model.applyGoodsSnapshot(10L, new SkuSnapshot(2001L, 10L, 7L, "示例店铺", "示例商品",
                "http://img/x.png", Map.of(), new BigDecimal("10.00"), true, true, true, false));
        model.applyPrice(10L, new BigDecimal("10.00"));
        model.seal();
        return model;
    }

    // ── 装配期：配置不合法一律起不来 ──────────────────────────────────────────────

    @Test
    @DisplayName("配置为空 / null → IllegalStateException")
    void emptyConfigRejected() {
        assertThatThrownBy(() -> new OrderStatusFlow(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OrderStatusFlow(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("配置里有重复状态 → IllegalStateException（重复会让「下标 +1」判据失效）")
    void duplicateRejected() {
        List<OrderStatus> duplicated = List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.RECEIVED);

        assertThatThrownBy(() -> new OrderStatusFlow(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复状态")
                .hasMessageContaining("PAID");
    }

    @Test
    @DisplayName("配置里有 null 元素 → IllegalStateException")
    void nullElementRejected() {
        List<OrderStatus> withNull = new ArrayList<>();
        withNull.add(OrderStatus.PENDING_PAYMENT);
        withNull.add(null);

        assertThatThrownBy(() -> new OrderStatusFlow(withNull))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空值");
    }

    @Test
    @DisplayName("配置缺枚举常量 → IllegalStateException，且消息点名缺了哪个（缺一个就有状态永远到不了）")
    void missingConstantRejected() {
        assertThatThrownBy(() -> new OrderStatusFlow(
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少状态")
                .hasMessageContaining("RECEIVED");
    }

    @Test
    @DisplayName("覆盖全部常量即可装配，statuses() 回不可变副本")
    void fullCoverageAccepted() {
        OrderStatusFlow flow = flow();

        assertThat(flow.statuses()).containsExactlyElementsOf(FULL_FLOW);
        assertThat(flow.statuses()).isEqualTo(FULL_FLOW);
        assertThatThrownBy(() -> flow.statuses().add(OrderStatus.RECEIVED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── 顺序由配置决定，代码里没有 if/else ────────────────────────────────────────

    @Test
    @DisplayName("next 按配置的下标走：待支付 → 已支付 → 已发货 → 已收货")
    void nextWalksConfiguredOrder() {
        OrderStatusFlow flow = flow();

        assertThat(flow.next(OrderStatus.PENDING_PAYMENT)).isEqualTo(OrderStatus.PAID);
        assertThat(flow.next(OrderStatus.PAID)).isEqualTo(OrderStatus.SHIPPED);
        assertThat(flow.next(OrderStatus.SHIPPED)).isEqualTo(OrderStatus.RECEIVED);
    }

    @Test
    @DisplayName("换一份配置，同一个「待支付」的下一个状态就跟着变（证明顺序来自配置而非代码）")
    void orderComesFromConfig() {
        List<OrderStatus> shippedFirst = List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.SHIPPED,
                OrderStatus.PAID, OrderStatus.RECEIVED);
        OrderStatusFlow flow = new OrderStatusFlow(shippedFirst);

        assertThat(flow.next(OrderStatus.PENDING_PAYMENT)).isEqualTo(OrderStatus.SHIPPED);
        assertThatCode(() -> flow.assertCanTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.SHIPPED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> flow.assertCanTransition(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("已是最后一个状态 → ServiceException(400)，提示可读")
    void nextOnLastStateRejected() {
        Throwable thrown = catchThrowable(() -> flow().next(OrderStatus.RECEIVED));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) thrown).getCode()).isEqualTo(400);
        assertThat(thrown.getMessage()).contains("已收货");
    }

    // ── 迁移：只允许下标 +1 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("逐级推进合法，且状态与轨迹同步前进")
    void legalTransitionAdvancesStatus() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();

        flow.transition(order, OrderStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);

        flow.transition(order, OrderStatus.SHIPPED);
        flow.transition(order, OrderStatus.RECEIVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
    }

    @Test
    @DisplayName("跳级 → ServiceException(400)，消息含 from/to 两侧 mallLabel")
    void skipLevelRejected() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();

        Throwable thrown = catchThrowable(() -> flow.transition(order, OrderStatus.RECEIVED));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) thrown).getCode()).isEqualTo(400);
        assertThat(thrown.getMessage()).contains("待支付").contains("已收货").contains("跳级");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getStatusTrail()).hasSize(1);
    }

    @Test
    @DisplayName("回退 → ServiceException(400)，提示里能看出是「回退」")
    void backwardRejected() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();
        flow.transition(order, OrderStatus.PAID);

        Throwable thrown = catchThrowable(() -> flow.assertCanTransition(OrderStatus.PAID, OrderStatus.PENDING_PAYMENT));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        // 提示语用 mallLabel：PAID 在提示里是「已支付」（店主侧的「待发货」不进这句文案）
        assertThat(thrown.getMessage()).contains("回退").contains("已支付").contains("待支付");
    }

    @Test
    @DisplayName("原地重复变更 → ServiceException(400)（同一状态再置一次不是幂等，是调用方逻辑错）")
    void repeatedTransitionRejected() {
        OrderStatusFlow flow = flow();

        Throwable thrown = catchThrowable(() -> flow.assertCanTransition(OrderStatus.PAID, OrderStatus.PAID));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).contains("重复变更");
    }

    @Test
    @DisplayName("未 seal 的订单不能迁移：从 transition 直接进来也是 IllegalStateException（异常类型不因入口而变）")
    void unsealedOrderCannotTransition() {
        OrderStatusFlow flow = flow();
        OrderModel unsealed = OrderModel.open("202609211200000002", 11L, 7L, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-2", "fp-2", LocalDateTime.of(2026, 9, 21, 12, 0, 0), List.of(new OrderLine(10L, 1)));

        assertThatThrownBy(() -> flow.transition(unsealed, OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未封存");
    }

    @Test
    @DisplayName("目标状态 / 当前状态为空 → NPE（编程错误）")
    void nullStatesRejected() {
        OrderStatusFlow flow = flow();

        assertThatThrownBy(() -> flow.assertCanTransition(OrderStatus.PENDING_PAYMENT, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> flow.assertCanTransition(null, OrderStatus.PAID))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> flow.transition(null, OrderStatus.PAID))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("覆盖全部常量的配置就是 OrderStatus.values()（钉住本测试与生产配置同源）")
    void fullFlowMatchesEnumConstants() {
        assertThat(FULL_FLOW).containsExactlyElementsOf(Arrays.asList(OrderStatus.values()));
    }
}
