package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.support.InMemoryStockPort;
import com.panoramic.trade.order.support.OrderStatusChain;
import com.panoramic.trade.order.support.StockOutboundRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 取消 / 仅退款两条终止动作（同一个服务、两份调用）：**状态推到哪个终态、库存还回去、被拒时一个字都不写**。
 *
 * <p>⚠ 本类守的是三件事，三件都不会以别的方式自己暴露：</p>
 * <ol>
 *   <li><b>闸门在状态机，不在服务里</b>：待支付才能取消、已支付（未发货）才能仅退款。写错的代价是
 *       「已发货的单被退款」——库存已出库、货已上路，而订单显示「已退款」。故四条非法组合各一条用例，
 *       且断言**异常文案**（两侧 mallLabel 拼的中文，原样透传给用户的就是它）。</li>
 *   <li><b>回补库存与迁移是同一笔动作</b>：只迁状态不还库存 = 少卖（货架永远少那几件），
 *       只还库存不迁状态 = 超卖（顾客还能再付一次）。两条正向用例各自断言「状态变了 **且** 净出库回到 0」。</li>
 *   <li><b>被拒时没有任何副作用</b>：状态、轨迹、库存、流水四处都不许动——第 1 步（聚合内迁移）
 *       拒掉之后就走不到第 2、3 步，本类把这个顺序当成可观察事实来断言。</li>
 * </ol>
 *
 * <p>⚠ 用内存端口 + 真状态机、**不启 Spring**（同 {@code OrderRollbackTest} 的口径）。
 * ⚠ 内存仓库表达不了「条件更新拿到 0 行 → 400」那条并发判据（见 {@link InMemoryOrderRepository#update}），
 * 故本类验的是**顺序重复调用**（第二次被状态机拒），并发那一幕只能在真库上验。</p>
 */
class OrderCancelServiceTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 9, 21, 12, 0);
    private static final Long CUSTOMER_ID = 11L;
    private static final Long STORE_ID = 7L;
    private static final Long SKU_A = 10L;
    private static final int QUANTITY = 2;
    private static final int STOCK = 5;

    /** 收货地址与各用例的断言无关，取一份合法值即可（地址校验在 {@code OrderAddress} 自己那侧） */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderStatusFlow statusFlow;
    private OrderCancelService cancelService;

    /** 单号 / 指纹的序号：同一用例里要两笔单时保证它们不是同一笔（单号是 {orderNo, skuId} 之外的唯一标识） */
    private int sequence;

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        stockPort = new InMemoryStockPort(clock);
        stockPort.setStock(SKU_A, STOCK);
        orderRepository = new InMemoryOrderRepository();
        statusFlow = new OrderStatusFlow(OrderStatusChain.production());
        cancelService = new OrderCancelService(orderRepository, stockPort, statusFlow);
        sequence = 0;
    }

    // ── 正向：两条动作各自「状态到位 + 库存还回去」 ──────────────────────────────

    @Test
    @DisplayName("待支付取消：状态「已取消」、轨迹追加一项、库存回补到原值（净出库 0）")
    void cancelPendingPaymentRevertsStock() {
        OrderModel order = pendingOrder();
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);

        cancelService.cancel(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 轨迹是「这笔单走过哪些状态」：追加一项，不是把待支付改掉（seq 的连续性靠它）
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(netOutbound()).isZero();
        assertThat(stockPort.outboundRecords()).extracting(StockOutboundRecord::quantity)
                .containsExactly(QUANTITY, -QUANTITY);
    }

    @Test
    @DisplayName("已支付仅退款：状态「已退款」、轨迹三项、库存回补（一步生效、无中间态）")
    void refundPaidOrderRevertsStock() {
        OrderModel order = paidOrder();
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);

        cancelService.refund(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        // ⚠ 没有「退款中」这种中间状态：一步生效（todo：无需商户同意），轨迹里不许出现第四项
        assertThat(order.getStatusTrail())
                .containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.REFUNDED);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(netOutbound()).isZero();
    }

    // ── 反向：四条非法组合，各自一个字都不写 ──────────────────────────────────────

    @Test
    @DisplayName("已支付取消 → 400，状态 / 轨迹 / 库存全不动")
    void cancellingPaidOrderIsRejected() {
        OrderModel order = paidOrder();

        assertThatThrownBy(() -> cancelService.cancel(order))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单状态不能从「已支付」变更为「已取消」");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);   // 没还
        assertThat(stockPort.outboundRecords()).hasSize(1);                   // 也没留反向流水
    }

    @Test
    @DisplayName("待支付仅退款 → 400（没付过款，谈不上退）")
    void refundingUnpaidOrderIsRejected() {
        OrderModel order = pendingOrder();

        assertThatThrownBy(() -> cancelService.refund(order))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单状态不能从「待支付」变更为「已退款」");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);
    }

    @Test
    @DisplayName("已发货仅退款 → 400（货已上路，仅退款到「已支付未发货」为止）")
    void refundingShippedOrderIsRejected() {
        OrderModel order = paidOrder();
        order.markShipped(statusFlow, "SF1234567890");

        assertThatThrownBy(() -> cancelService.refund(order))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单状态不能从「已发货」变更为「已退款」");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);
    }

    @Test
    @DisplayName("重复取消 → 400 原地文案，且不会二次回补（流水仍只有两条）")
    void cancellingTwiceIsRejectedAndDoesNotRevertAgain() {
        OrderModel order = pendingOrder();
        cancelService.cancel(order);

        assertThatThrownBy(() -> cancelService.cancel(order))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单状态不能从「已取消」重复变更到「已取消」");

        // ⚠ 这道保险值得单独断言：若第二次真走到回补，库存会被还成 5+2=7（比原值还多）——多卖的镜像错误
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(stockPort.outboundRecords()).hasSize(2);
    }

    @Test
    @DisplayName("已取消后再仅退款 → 400（两个结束过程的落点互不相通）")
    void refundingCancelledOrderIsRejected() {
        OrderModel order = pendingOrder();
        cancelService.cancel(order);

        assertThatThrownBy(() -> cancelService.refund(order))
                .isInstanceOf(ServiceException.class)
                .hasMessage("订单状态不能从「已取消」变更为「已退款」");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────────

    /**
     * 一笔**已落库**的「待支付」单（{@value #QUANTITY} 件 SKU_A，各 10.00 元，库存按下单即扣的口径扣掉）
     *
     * <p>⚠ 造的是真聚合（{@code open} → 补快照 → 计价 → seal）+ 真库存端口扣减，而不是拿一个状态字段
     * 拼出来的替身：取消这一路读的正是「封存后的总额 / 轨迹 / seal 标记」，替身少一样就会让
     * 「只有替身上绿」的用例出现。</p>
     */
    private OrderModel pendingOrder() {
        sequence++;
        String orderNo = String.format("2026092112000000%02d", sequence);
        OrderModel order = OrderModel.open(orderNo, CUSTOMER_ID, STORE_ID, "示例店铺", OrderSource.DIRECT,
                ADDRESS, "req-" + sequence, "fp-" + sequence, CREATE_TIME, CREATE_TIME.plusMinutes(10),
                List.of(new OrderLine(SKU_A, QUANTITY)));
        order.applyGoodsSnapshot(SKU_A, snapshot());
        order.applyPrice(SKU_A, new BigDecimal("10.00"));
        order.seal();
        assertThat(stockPort.deduct(SKU_A, QUANTITY, orderNo)).isTrue();
        // 落库：聚合在仓库里（update 会断言这笔单确实存在，不先落库那条断言就没意义）
        long submissionId = orderRepository.occupy(CUSTOMER_ID, null).submissionId();
        orderRepository.saveAll(submissionId, List.of(order));
        return order;
    }

    /** 一笔「已支付」的单（= 上面那笔 + 付全额） */
    private OrderModel paidOrder() {
        OrderModel order = pendingOrder();
        order.markPaid(statusFlow, order.getTotalAmount());
        return order;
    }

    private static SkuSnapshot snapshot() {
        return new SkuSnapshot(2000L + SKU_A, SKU_A, STORE_ID, "示例店铺", "商品" + SKU_A,
                "http://img/" + SKU_A + ".png", Map.of("颜色", "黑"), new BigDecimal("10.00"), true, true, true, false);
    }

    /** 出库流水的净量：正数出库、负数回补，「净出库 0」就等于「库存回到原值」 */
    private int netOutbound() {
        return stockPort.outboundRecords().stream().mapToInt(StockOutboundRecord::quantity).sum();
    }
}
