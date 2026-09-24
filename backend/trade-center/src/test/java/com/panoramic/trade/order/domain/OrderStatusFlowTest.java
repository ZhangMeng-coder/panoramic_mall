package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.support.OrderStatusChain;
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
 * 状态机 = **一条线性主链（来自配置）+ 两个写死在动作里的结束过程**。
 *
 * <p>本类守四件事：① 配置错一律装配期失败；② 主链推进的判据只有「配置里的下一个」（跳级 / 回退 / 重复全拒）；
 * ③ 两个结束过程的前置状态由**动作**声明（本类经 {@code OrderModel} 的动作调用，不自己拼 from）；
 * ④ 轨迹必须是「主链的一段前缀 + 至多一个结束过程收尾」。</p>
 *
 * <p>⚠ 「配置错」与「迁移非法」用两种异常（裁定 D12）：前者是程序员错误（{@code IllegalStateException}，
 * 服务起不来最好），后者是用户可见的业务错（{@code ServiceException(400)}，提示直接给页面看）。</p>
 */
class OrderStatusFlowTest {

    /**
     * 与 {@code application.yml} 同构的主链，取自共用夹具 {@link OrderStatusChain}
     * （主链本身不在这里写第二遍；yml ↔ 代码的对账在 {@code OrderDomainWiringTest} 里做）。
     *
     * <p>⚠ <b>这里只有主链</b>：两个结束过程（待支付 → 已取消、已支付 → 已退款）的前置状态写在
     * {@code OrderModel#markCancelled} / {@code #markRefunded} 里，**不配在这里**，配进来反而会被
     * 构造器拒绝——见 {@link #endingTargetInConfigRejected()}。</p>
     */
    private static List<OrderStatus> chain() {
        return OrderStatusChain.production();
    }

    /** 以真实主链为底，把某一项换成另一个状态（构造非法配置用） */
    private static List<OrderStatus> chainWith(OrderStatus replaced, OrderStatus replacement) {
        List<OrderStatus> copy = new ArrayList<>(chain());
        copy.set(copy.indexOf(replaced), replacement);
        return copy;
    }

    /** 以真实主链为底，删掉某一项（构造「缺常量」用） */
    private static List<OrderStatus> chainWithout(OrderStatus status) {
        List<OrderStatus> copy = new ArrayList<>(chain());
        copy.remove(status);
        return copy;
    }

    /** 以真实主链为底，把某一项挪到末尾（构造「主链末项不是已收货」用） */
    private static List<OrderStatus> chainMovedToEnd(OrderStatus status) {
        List<OrderStatus> copy = new ArrayList<>(chain());
        copy.remove(status);
        copy.add(status);
        return copy;
    }

    private static OrderStatusFlow flow() {
        return new OrderStatusFlow(chain());
    }

    private static OrderModel sealedOrder() {
        OrderModel model = OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-1", "fp-1", LocalDateTime.of(2026, 9, 21, 12, 0, 0), LocalDateTime.of(2026, 9, 21, 12, 10, 0),
                List.of(new OrderLine(10L, 1)));
        model.applyGoodsSnapshot(10L, new SkuSnapshot(2001L, 10L, 7L, "示例店铺", "示例商品",
                "http://img/x.png", Map.of(), new BigDecimal("10.00"), true, true, true, false));
        model.applyPrice(10L, new BigDecimal("10.00"));
        model.seal();
        return model;
    }

    private static OrderModel unsealedOrder() {
        return OrderModel.open("202609211200000002", 11L, 7L, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-2", "fp-2", LocalDateTime.of(2026, 9, 21, 12, 0, 0), LocalDateTime.of(2026, 9, 21, 12, 10, 0),
                List.of(new OrderLine(10L, 1)));
    }

    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    // ── 装配期：配置不合法一律起不来 ──────────────────────────────────────────────

    @Test
    @DisplayName("配置为空 / null → IllegalStateException")
    void emptyConfigRejected() {
        assertThatThrownBy(() -> new OrderStatusFlow(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OrderStatusFlow(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("配置里有 null 元素 → IllegalStateException")
    void nullElementRejected() {
        List<OrderStatus> withNull = new ArrayList<>(chain());
        withNull.set(1, null);

        assertThatThrownBy(() -> new OrderStatusFlow(withNull))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("空值");
    }

    @Test
    @DisplayName("配置里同一个状态出现两次 → IllegalStateException（顺序即先后，重复就没有先后可言）")
    void duplicateRejected() {
        // 把 PAID 换成 SHIPPED：得到 [待支付, 已发货, 已发货, 已收货]
        List<OrderStatus> duplicated = chainWith(OrderStatus.PAID, OrderStatus.SHIPPED);

        assertThatThrownBy(() -> new OrderStatusFlow(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("出现了不止一次")
                .hasMessageContaining("SHIPPED");
    }

    @Test
    @DisplayName("配置里出现两个结束过程的落点 → IllegalStateException（前置状态写在动作里，配了就是第二份定义）")
    void endingTargetInConfigRejected() {
        // 已取消放在末尾（最自然的位置）也一样拒绝
        assertThatThrownBy(() -> new OrderStatusFlow(chainWith(OrderStatus.RECEIVED, OrderStatus.CANCELLED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("结束过程")
                .hasMessageContaining("CANCELLED");

        // 已退款塞在中间（假装它是一条主链边）同样拒绝——判据是「配没配」，不是「配得像不像」
        assertThatThrownBy(() -> new OrderStatusFlow(chainWith(OrderStatus.SHIPPED, OrderStatus.REFUNDED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("结束过程")
                .hasMessageContaining("REFUNDED");
    }

    @Test
    @DisplayName("配置首项不是「待支付」→ IllegalStateException（开单即待支付写在 OrderModel#open 里，不来自配置）")
    void firstItemMustBePendingPayment() {
        // 主链写全了（含待支付），只是把它排在后面：覆盖断言过得去，卡在首项这一条上
        List<OrderStatus> rotated = new ArrayList<>(chain());
        rotated.remove(OrderStatus.PENDING_PAYMENT);
        rotated.add(OrderStatus.PENDING_PAYMENT);

        assertThatThrownBy(() -> new OrderStatusFlow(rotated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("首项")
                .hasMessageContaining("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("配置缺枚举常量 → IllegalStateException，且消息点名缺了哪个（缺一个就有状态永远到不了）")
    void missingConstantRejected() {
        assertThatThrownBy(() -> new OrderStatusFlow(chainWithout(OrderStatus.RECEIVED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少状态")
                .hasMessageContaining("RECEIVED");
    }

    @Test
    @DisplayName("主链末项加两个落点 ≠ OrderStatus 声明的「已结束」→ IllegalStateException（去重口径会被悄悄改坏）")
    void endedDeclarationMustMatchSinks() {
        // [待支付, 已发货, 已收货, 已支付]：覆盖全、首项对、无重复，但「走到头」被说成了已支付，
        // 而 OrderStatus#ENDED 声明的是已收货 / 已取消 / 已退款。两边不一致的后果是
        // 一笔已走到头的单被当成在途单**复用来顶掉新单**（静默错），故宁可起不来。
        assertThatThrownBy(() -> new OrderStatusFlow(chainMovedToEnd(OrderStatus.PAID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已结束")
                .hasMessageContaining("PAID")
                .hasMessageContaining("RECEIVED");
    }

    @Test
    @DisplayName("主链配全即可装配：顺序原样保留、入口是首项、末项与两个落点都没有下一步")
    void fullChainAccepted() {
        OrderStatusFlow flow = flow();

        assertThat(flow.chain()).containsExactlyElementsOf(chain());
        assertThat(flow.initialState()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(flow.nextOf(OrderStatus.PENDING_PAYMENT)).isEqualTo(OrderStatus.PAID);
        assertThat(flow.nextOf(OrderStatus.PAID)).isEqualTo(OrderStatus.SHIPPED);
        assertThat(flow.nextOf(OrderStatus.SHIPPED)).isEqualTo(OrderStatus.RECEIVED);
        // 末项（正道走到头）与两个结束过程的落点（不在主链上）都没有「下一个」
        assertThat(flow.nextOf(OrderStatus.RECEIVED)).isNull();
        assertThat(flow.nextOf(OrderStatus.CANCELLED)).isNull();
        assertThat(flow.nextOf(OrderStatus.REFUNDED)).isNull();
        // 返回的是不可变副本（调用方拿到就能直接持有，改不动）
        assertThatThrownBy(() -> flow.chain().add(OrderStatus.CANCELLED))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("查 null 状态的下一步 → NPE；合法状态正常返回（编程错误）")
    void unknownStatusRejected() {
        OrderStatusFlow flow = flow();

        assertThatThrownBy(() -> flow.nextOf(null)).isInstanceOf(NullPointerException.class);
        assertThatCode(() -> flow.nextOf(OrderStatus.PAID)).doesNotThrowAnyException();
    }

    // ── 主链推进：判据只有「配置里的下一个」 ──────────────────────────────────────

    @Test
    @DisplayName("沿主链逐步推进合法，且状态与轨迹同步前进")
    void advanceWalksTheChain() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();

        flow.advance(order, OrderStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);

        flow.advance(order, OrderStatus.SHIPPED);
        flow.advance(order, OrderStatus.RECEIVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                OrderStatus.SHIPPED, OrderStatus.RECEIVED);
    }

    @Test
    @DisplayName("跳级 / 回退 → ServiceException(400)，消息含 from/to 两侧 mallLabel")
    void skipAndBackRejected() {
        OrderStatusFlow flow = flow();

        // 未支付就想发货：主链上待支付的下一个是已支付
        Throwable skipped = catchThrowable(() -> flow.advance(sealedOrder(), OrderStatus.SHIPPED));
        assertThat(skipped).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) skipped).getCode()).isEqualTo(400);
        // 提示语用的是 mallLabel（给顾客看的），故 SHIPPED 在这里是「已发货」而不是店主侧的「待收货」
        assertThat(skipped.getMessage()).contains("变更为").contains("待支付").contains("已发货");

        // 往回走：已支付的单不能再回到待支付
        OrderModel order = sealedOrder();
        flow.advance(order, OrderStatus.PAID);
        Throwable back = catchThrowable(() -> flow.advance(order, OrderStatus.PENDING_PAYMENT));
        assertThat(back).isInstanceOf(ServiceException.class);
        assertThat(back.getMessage()).contains("已支付").contains("待支付");
    }

    @Test
    @DisplayName("原地重复推进 → ServiceException(400)（同一状态再置一次不是幂等，是调用方逻辑错）")
    void repeatedAdvanceRejected() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();
        flow.advance(order, OrderStatus.PAID);

        Throwable thrown = catchThrowable(() -> flow.advance(order, OrderStatus.PAID));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        // ⚠ 这一句措辞被 mall-bff.md 的契约注释引用，改它要同批改契约
        assertThat(thrown.getMessage()).contains("重复变更");
    }

    @Test
    @DisplayName("换一份主链，同一个状态的下一步就跟着变（证明主链的判据来自配置而非代码）")
    void chainComesFromConfig() {
        // 「先发货后付款」的口径：把 SHIPPED 挪到 PAID 前面
        OrderStatusFlow swapped = new OrderStatusFlow(List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.SHIPPED,
                OrderStatus.PAID, OrderStatus.RECEIVED));
        OrderStatusFlow production = flow();

        assertThat(swapped.nextOf(OrderStatus.PENDING_PAYMENT)).isEqualTo(OrderStatus.SHIPPED);
        assertThatCode(() -> swapped.advance(sealedOrder(), OrderStatus.SHIPPED))
                .as("在 swapped 这份主链里，「待支付 → 已发货」就是下一步")
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> production.advance(sealedOrder(), OrderStatus.SHIPPED))
                .as("在生产那份主链里，同一对是跳级")
                .isInstanceOf(ServiceException.class);

        OrderModel order = sealedOrder();
        swapped.advance(order, OrderStatus.SHIPPED);
        swapped.advance(order, OrderStatus.PAID);
        assertThatThrownBy(() -> swapped.advance(order, OrderStatus.SHIPPED))
                .as("已支付在这里的下一个是已收货，不是已发货")
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("未 seal 的订单不能推进：从 advance 直接进来也是 IllegalStateException（异常类型不因入口而变）")
    void unsealedOrderCannotAdvance() {
        OrderStatusFlow flow = flow();

        assertThatThrownBy(() -> flow.advance(unsealedOrder(), OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未封存");
    }

    @Test
    @DisplayName("订单 / 目标状态 / 状态机为空 → NPE（编程错误）")
    void nullStatesRejected() {
        OrderStatusFlow flow = flow();

        assertThatThrownBy(() -> flow.advance(null, OrderStatus.PAID)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> flow.advance(sealedOrder(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> flow.endWith(null, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sealedOrder().markCancelled(null)).isInstanceOf(NullPointerException.class);
    }

    // ── 结束过程：前置状态由动作给出，状态机只做比对 ──────────────────────────────

    @Test
    @DisplayName("取消：待支付 → 已取消，状态与轨迹同步前进")
    void cancelFromPendingPayment() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();

        order.markCancelled(flow);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("取消：已支付 → 400（「只能从未支付来」这个前置状态写在 markCancelled 里）")
    void cancelAfterPaidRejected() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();
        order.markPaid(flow, new BigDecimal("10.00"));

        Throwable thrown = catchThrowable(() -> order.markCancelled(flow));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).contains("已支付").contains("已取消");
        // 被拒之后模型一点没动（状态与轨迹都不留痕）
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
    }

    @Test
    @DisplayName("仅退款：已支付 → 已退款，状态与轨迹同步前进")
    void refundFromPaid() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();
        order.markPaid(flow, new BigDecimal("10.00"));

        order.markRefunded(flow);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("仅退款：待支付 → 400（「只能从已支付来」这个前置状态写在 markRefunded 里）")
    void refundBeforePaidRejected() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();

        Throwable thrown = catchThrowable(() -> order.markRefunded(flow));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).contains("待支付").contains("已退款");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("结束之后就再没有下一步：已取消 / 已退款 / 已收货之后任何动作都被拒")
    void endedOrderCannotMoveAnyFurther() {
        OrderStatusFlow flow = flow();

        OrderModel cancelled = sealedOrder();
        cancelled.markCancelled(flow);
        assertThatThrownBy(() -> cancelled.markPaid(flow, new BigDecimal("10.00")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已取消");

        OrderModel refunded = sealedOrder();
        refunded.markPaid(flow, new BigDecimal("10.00"));
        refunded.markRefunded(flow);
        assertThatThrownBy(() -> refunded.markShipped(flow, "SF123"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已退款");

        OrderModel received = sealedOrder();
        received.markPaid(flow, new BigDecimal("10.00"));
        received.markShipped(flow, "SF123");
        received.markReceived(flow);
        assertThatThrownBy(() -> received.markReceived(flow))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重复变更");
    }

    @Test
    @DisplayName("已支付的单既能发货、也能仅退款——主链的下一步与那个结束过程并列合法")
    void twoWaysOutOfPaid() {
        OrderStatusFlow flow = flow();
        OrderModel shipped = sealedOrder();
        OrderModel refunded = sealedOrder();

        shipped.markPaid(flow, new BigDecimal("10.00"));
        shipped.markShipped(flow, "SF123");
        refunded.markPaid(flow, new BigDecimal("10.00"));
        refunded.markRefunded(flow);

        assertThat(shipped.getStatusTrail())
                .containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.SHIPPED);
        assertThat(refunded.getStatusTrail())
                .containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("拿结束过程走主链 → IllegalStateException（主链的下一步只能由 advance 推，否则配置说了不算）")
    void endingProcessCannotCarryTheMainLine() {
        OrderStatusFlow flow = flow();
        OrderModel order = sealedOrder();

        assertThatThrownBy(() -> flow.endWith(order, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("在主链上");
        // 被拒之后模型没动
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    // ── 轨迹校验：主链的一段前缀 + 至多一个结束过程收尾（读侧与写侧共用这份判据） ──────

    @Test
    @DisplayName("合法轨迹放行：主线的、走取消的、走仅退款的都算合法")
    void legalTrailsAccepted() {
        OrderStatusFlow flow = flow();

        assertThatCode(() -> flow.assertLegalTrail("N1", List.of(OrderStatus.PENDING_PAYMENT)))
                .doesNotThrowAnyException();
        assertThatCode(() -> flow.assertLegalTrail("N2", List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)))
                .doesNotThrowAnyException();
        assertThatCode(() -> flow.assertLegalTrail("N3", List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED)))
                .doesNotThrowAnyException();
        assertThatCode(() -> flow.assertLegalTrail("N4", List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                OrderStatus.REFUNDED))).doesNotThrowAnyException();
        assertThatCode(() -> flow.assertLegalTrail("N5", List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                OrderStatus.SHIPPED, OrderStatus.RECEIVED))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非法轨迹 → IllegalStateException：空 / 不从入口起 / 跳级 / 回退 / 落在主链外却不是末项 / 越过主链末尾")
    void illegalTrailsRejected() {
        OrderStatusFlow flow = flow();

        assertThatThrownBy(() -> flow.assertLegalTrail("N1", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("轨迹为空");
        // 从中间开始（首项不是入口状态）
        assertThatThrownBy(() -> flow.assertLegalTrail("N2", List.of(OrderStatus.PAID, OrderStatus.SHIPPED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是从初始状态开始的")
                .hasMessageContaining("PENDING_PAYMENT");
        // 跳过了中间的边（轨迹第 2 项不是主链第 2 项）
        assertThatThrownBy(() -> flow.assertLegalTrail("N3",
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.SHIPPED)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是一条合法路径")
                .hasMessageContaining("SHIPPED");
        // 往回走
        assertThatThrownBy(() -> flow.assertLegalTrail("N4",
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PENDING_PAYMENT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是一条合法路径");
        // 结束过程的落点不是末项（「结束之后又走了一步」）
        assertThatThrownBy(() -> flow.assertLegalTrail("N5",
                List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, OrderStatus.PAID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不是最后一项")
                .hasMessageContaining("CANCELLED");
        // 主链走完了还多一项
        assertThatThrownBy(() -> flow.assertLegalTrail("N6", List.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID,
                OrderStatus.SHIPPED, OrderStatus.RECEIVED, OrderStatus.PAID)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有更多状态");
        // 轨迹里混进一个坏值（null 只报「空值」，不去渲染它的名字）
        assertThatThrownBy(() -> flow.assertLegalTrail("N7", Arrays.asList(OrderStatus.PENDING_PAYMENT, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("第 2 项是空值");
    }
}
