package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.support.OrderStatusChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 订单聚合根：两段式生命周期（补全 → seal 冻结）、金额与行求和一致、状态轨迹、对外不可变。
 *
 * <p>⚠ 本类刻意**不启 Spring**（裁定 D10）：领域模型的封闭性只能靠纯 JUnit 断言，
 * 一旦要起上下文，测的就不是模型而是装配了。</p>
 */
class OrderModelTest {

    /**
     * 与 {@code application.yml} 同构的状态主链（本类只验模型行为，主链本身写在 {@link OrderStatusChain}；
     * yml ↔ 代码的对账在 {@code OrderDomainWiringTest}）。
     */
    private static final OrderStatusFlow FLOW = new OrderStatusFlow(OrderStatusChain.production());

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 9, 21, 12, 0, 0);

    /** 发货时填的快递单号（取值本身无意义，只要合法：非空且不超过长度上限） */
    private static final String TRACKING_NO = "SF1234567890";

    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");


    private static OrderModel openOrder(OrderLine... lines) {
        return OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-1", "fp-1", CREATE_TIME, CREATE_TIME.plusMinutes(10), List.of(lines));
    }

    private static SkuSnapshot snapshot(long skuId, String price) {
        return new SkuSnapshot(2000L + skuId, skuId, 7L, "示例店铺", "商品" + skuId, "http://img/" + skuId + ".png",
                Map.of("颜色", "黑"), new BigDecimal(price), true, true, true, false);
    }

    /** 把两行（10 号 2 件、20 号 3 件，各 10.00 元）补全成一个可 seal 的订单：合计 5 件 50.00 元 */
    private static OrderModel completableOrder() {
        OrderModel model = openOrder(new OrderLine(10L, 2), new OrderLine(20L, 3));
        model.getItems().forEach(item -> model.applyGoodsSnapshot(item.getSkuId(), snapshot(item.getSkuId(), "10.00")));
        model.getItems().forEach(item -> model.applyPrice(item.getSkuId(), new BigDecimal("10.00")));
        return model;
    }

    // ── 开单：初始状态与确定性 ────────────────────────────────────────────────────

    @Test
    @DisplayName("open 后即「待支付」，轨迹只有初始状态，未 seal")
    void openedOrderRestsAtPendingPayment() {
        OrderModel model = openOrder(new OrderLine(10L, 2), new OrderLine(20L, 1));

        assertThat(model.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(model.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT);
        assertThat(model.isSealed()).isFalse();
        assertThat(model.getTotalQuantity()).isEqualTo(3);
        assertThat(model.getTotalAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("订单行按 skuId 升序排好（入参顺序不同也得到同一个内部状态）")
    void itemsAreSortedBySkuId() {
        OrderModel model = openOrder(new OrderLine(30L, 1), new OrderLine(10L, 1), new OrderLine(20L, 1));

        assertThat(model.getItems()).extracting(OrderItem::getSkuId).containsExactly(10L, 20L, 30L);
    }

    @Test
    @DisplayName("开单透传各字段（单号 / 锚点 / 店铺名 / 来源 / 幂等键 / 指纹 / 下单时间）")
    void openCarriesAllFields() {
        OrderModel model = openOrder(new OrderLine(10L, 1));

        assertThat(model.getOrderNo()).isEqualTo("202609211200000001");
        assertThat(model.getCustomerId()).isEqualTo(11L);
        assertThat(model.getStoreId()).isEqualTo(7L);
        assertThat(model.getStoreName()).isEqualTo("示例店铺");
        assertThat(model.getSource()).isEqualTo(OrderSource.DIRECT);
        assertThat(model.getRequestId()).isEqualTo("req-1");
        assertThat(model.getFingerprint()).isEqualTo("fp-1");
        assertThat(model.getCreateTime()).isEqualTo(CREATE_TIME);
        assertThat(model.getExpireTime()).isEqualTo(CREATE_TIME.plusMinutes(10));
    }

    @Test
    @DisplayName("支付截止时刻不晚于下单时刻 → IllegalStateException（时限配成 0/负数的落点，不是用户输入问题）")
    void deadlineNotAfterCreateTimeRejected() {
        assertThatThrownBy(() -> OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT,
                ADDRESS, "req-1", "fp-1", CREATE_TIME, CREATE_TIME, List.of(new OrderLine(10L, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不晚于下单时刻");
        assertThatThrownBy(() -> OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT,
                ADDRESS, "req-1", "fp-1", CREATE_TIME, CREATE_TIME.minusMinutes(1), List.of(new OrderLine(10L, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不晚于下单时刻");
    }

    @Test
    @DisplayName("新单缺支付截止时刻 → NPE（重建路径才允许 null：那是本列上线前的老单，语义是无超时）")
    void missingDeadlineRejectedOnOpen() {
        assertThatThrownBy(() -> OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT,
                ADDRESS, "req-1", "fp-1", CREATE_TIME, null, List.of(new OrderLine(10L, 1))))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("支付截止时刻");
    }

    @Test
    @DisplayName("同一 skuId 出现两次 → IllegalArgumentException（合并是调用方的职责，裁定 D14）")
    void duplicateSkuIdRejected() {
        assertThatThrownBy(() -> openOrder(new OrderLine(10L, 1), new OrderLine(10L, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出现多次");
    }

    @Test
    @DisplayName("数量越界在建行时即被拒（0 / 1000）")
    void outOfRangeQuantityRejected() {
        assertThatThrownBy(() -> openOrder(new OrderLine(10L, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> openOrder(new OrderLine(10L, 1000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空行列表可以开出来，但 seal 时必然被拦（唯一的不变量落点）")
    void emptyOrderCannotBeSealed() {
        OrderModel model = openOrder();

        assertThat(model.getItems()).isEmpty();
        assertThat(model.getTotalQuantity()).isZero();
        assertThatThrownBy(model::seal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有任何订单行");
    }

    // ── 补全：按 skuId 定位，找错就报 ─────────────────────────────────────────────

    @Test
    @DisplayName("补全/计价指向不存在的 skuId → IllegalArgumentException（静默忽略等于这一步白跑）")
    void unknownSkuIdRejected() {
        OrderModel model = openOrder(new OrderLine(10L, 1));

        assertThatThrownBy(() -> model.applyGoodsSnapshot(999L, snapshot(999L, "10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有 skuId=999");
        assertThatThrownBy(() -> model.applyPrice(999L, new BigDecimal("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("总额随逐行定价推进；有一行未完整则 seal 被拦")
    void totalsTrackPartialPricing() {
        OrderModel model = openOrder(new OrderLine(10L, 2), new OrderLine(20L, 3));
        model.applyGoodsSnapshot(10L, snapshot(10L, "10.00"));
        model.applyPrice(10L, new BigDecimal("10.00"));

        assertThat(model.getTotalQuantity()).isEqualTo(5);
        assertThat(model.getTotalAmount()).isEqualByComparingTo("20.00");   // 只算已定价的那一行
        assertThatThrownBy(model::seal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("skuId=20");
    }

    @Test
    @DisplayName("只补快照不定价、或只定价没有快照，都不算完成（顺序错在 seal 时被拦）")
    void incompleteItemBlocksSeal() {
        OrderModel snapshotOnly = openOrder(new OrderLine(10L, 1));
        snapshotOnly.applyGoodsSnapshot(10L, snapshot(10L, "10.00"));
        assertThatThrownBy(snapshotOnly::seal).isInstanceOf(IllegalStateException.class);

        OrderModel priceOnly = openOrder(new OrderLine(10L, 1));
        assertThatThrownBy(() -> priceOnly.applyPrice(10L, new BigDecimal("10.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未补全商品快照");
    }

    // ── seal：金额与行求和对账，之后冻结 ──────────────────────────────────────────

    @Test
    @DisplayName("补全后 seal 通过；总额等于行小计之和（10.00×2 + 10.00×3 = 50.00）")
    void sealSucceedsWithConsistentTotals() {
        OrderModel model = completableOrder();
        model.seal();

        assertThat(model.isSealed()).isTrue();
        assertThat(model.getTotalQuantity()).isEqualTo(5);
        assertThat(model.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(model.getItems()).extracting(OrderItem::getSubtotal)
                .allSatisfy(subtotal -> assertThat(subtotal).isNotNull());
    }

    @Test
    @DisplayName("重复 seal 幂等（步骤重跑不该被一次性闸门卡住）")
    void sealIsIdempotent() {
        OrderModel model = completableOrder();
        model.seal();
        model.seal();

        assertThat(model.isSealed()).isTrue();
    }

    @Test
    @DisplayName("seal 之后任何改行入口都抛 IllegalStateException（订单行与金额已冻结）")
    void sealedOrderRejectsRowChanges() {
        OrderModel model = completableOrder();
        model.seal();

        assertThatThrownBy(() -> model.applyGoodsSnapshot(10L, snapshot(10L, "99.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已封存");
        assertThatThrownBy(() -> model.applyPrice(10L, new BigDecimal("99.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已封存");
        assertThat(model.getTotalAmount()).isEqualByComparingTo("50.00");
    }

    // ── 状态迁移：未 seal 不准迁移；seal 后按状态机逐级推进 ────────────────────────

    @Test
    @DisplayName("未 seal 的订单不能置状态——不管从哪个入口进来都是 IllegalStateException")
    void unsealedOrderCannotTransition() {
        OrderModel model = completableOrder();

        assertThatThrownBy(() -> model.markPaid(FLOW, model.getTotalAmount()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未封存");
        assertThatThrownBy(() -> FLOW.advance(model, OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未封存");
        assertThat(model.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("seal 后逐级推进到「已完成」，轨迹逐次累加（D16：只前不退在数据上可断言）")
    void statusTrailAccumulates() {
        OrderModel model = completableOrder();
        model.seal();

        model.markPaid(FLOW, model.getTotalAmount());
        assertThat(model.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(model.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);

        model.markShipped(FLOW, TRACKING_NO);
        model.markReceived(FLOW);
        assertThat(model.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(model.getStatusTrail()).containsExactly(
                OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED, OrderStatus.RECEIVED);
        // seal 之后订单行仍然冻结，状态迁移不放开它
        assertThat(model.isSealed()).isTrue();
    }

    @Test
    @DisplayName("跳级 / 重复变更一律 ServiceException(400)，且失败不留痕（轨迹不动）")
    void illegalTransitionsAreRejectedWithoutTrace() {
        OrderModel model = completableOrder();
        model.seal();

        // 未支付就想发货：主链上待支付的下一个是已支付，不是已发货
        Throwable notAnEdge = catchThrowable(() -> model.markShipped(FLOW, TRACKING_NO));
        assertThat(notAnEdge).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) notAnEdge).getCode()).isEqualTo(400);
        // 提示语用的是 mallLabel（给顾客看的），故 SHIPPED 在这里是「已发货」而不是店主侧的「待收货」
        assertThat(notAnEdge.getMessage()).contains("变更为").contains("待支付").contains("已发货");

        model.markPaid(FLOW, model.getTotalAmount());

        Throwable repeat = catchThrowable(() -> model.markPaid(FLOW, model.getTotalAmount()));
        assertThat(repeat).isInstanceOf(ServiceException.class);
        assertThat(repeat.getMessage()).contains("重复变更");

        // 已支付的单回不到待支付（主链上待支付的下一个只会是已支付）
        Throwable back = catchThrowable(() -> FLOW.advance(model, OrderStatus.PENDING_PAYMENT));
        assertThat(back).isInstanceOf(ServiceException.class);
        assertThat(back.getMessage()).contains("变更为").contains("已支付").contains("待支付");

        assertThat(model.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(model.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
    }

    @Test
    @DisplayName("状态机为空 → NPE（装配错误，不是用户错）")
    void nullFlowRejected() {
        OrderModel model = completableOrder();
        model.seal();

        assertThatThrownBy(() -> model.markPaid(null, model.getTotalAmount()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> model.markReceived(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── 对外不可变 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getItems() 与 getStatusTrail() 返回不可变副本，外部改不动聚合内部")
    void accessorsReturnImmutableCopies() {
        OrderModel model = completableOrder();

        assertThatThrownBy(() -> model.getItems().add(OrderItem.open(99L, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> model.getStatusTrail().add(OrderStatus.PAID))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(model.getItems()).hasSize(2);
        assertThat(model.getStatusTrail()).hasSize(1);
    }

    @Test
    @DisplayName("聚合根对外无 public setter")
    void hasNoPublicSetter() {
        assertThat(OrderModel.class.getMethods())
                .noneMatch(method -> method.getName().startsWith("set"));
    }
}
