package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.support.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.support.InMemoryStockPort;
import com.panoramic.trade.order.support.OrderStatusChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付前的**超时判定**（todo：「支付按钮调用时也需要做一下超时时间判断，如果已超时，提示该订单已过期」）。
 *
 * <p>⚠ 三条口径各有用例：</p>
 * <ol>
 *   <li><b>没到期照常能付</b>：判定写严了（比如把「不晚于」写成「早于」、或忘了取 {@code Clock}）
 *       会把所有正常支付一并拒掉——这是本判定最坏的坏法，故第一条就是正向用例；</li>
 *   <li><b>到点即过期</b>（{@code now == expireTime}）：闭区间，与关单取数的 {@code expire_time <= ?}、
 *       与 {@code OrderModel#isTimedOut} 是同一句话；</li>
 *   <li><b>过期优先于金额校验</b>：一笔过了点的单填错金额，结论必须是「已过期」而不是「金额不符」
 *       ——后者会让顾客以为改个金额还能付。</li>
 * </ol>
 *
 * <p>⚠ 另有一条「截止时刻为 NULL 的历史单仍可支付」：{@code null} = 无超时，判定漏了这一条，
 * 本列上线前的老单就再也付不了了（而这批单还在库里）。</p>
 *
 * <p>⚠ 本类**不断言「关单」**：支付路径只拒付、不顺手关单（关单是 {@link OrderTimeoutCloseTask} 的事，
 * 理由见 {@code OrderApplicationService#payOrder} 的注释——在同一事务里先取消再抛异常，
 * 取消会一起回滚）。故这里的断言是「状态**没变**」而不是「已取消」。</p>
 */
class OrderPayTimeoutTest {

    private static final Instant NOW_INSTANT = Instant.parse("2026-09-21T04:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    private static final Long CUSTOMER_ID = 11L;
    private static final Long STORE_ID = 7L;
    private static final Long SKU_A = 10L;
    private static final int QUANTITY = 2;
    private static final String TOTAL = "20.00";

    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private MutableClock clock;
    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderStatusFlow statusFlow;
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));
        InMemoryGoodsQueryPort goodsQueryPort = new InMemoryGoodsQueryPort();
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_A, STORE_ID, "示例店铺", "10.00"));
        stockPort = new InMemoryStockPort(clock);
        stockPort.setStock(SKU_A, 20);
        orderRepository = new InMemoryOrderRepository();
        statusFlow = new OrderStatusFlow(OrderStatusChain.production());

        OrderProperties properties = new OrderProperties();
        properties.setSteps(List.of(GoodsCheckStep.NAME, StockCheckStep.NAME, PriceComputeStep.NAME));
        properties.setStatusFlow(OrderStatusChain.production());
        properties.setIdempotencyWindowSeconds(300);
        properties.setOrderNoMaxRetry(5);
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goodsQueryPort), new StockCheckStep(stockPort),
                new PriceComputeStep(goodsQueryPort)), properties);
        OrderCreateCoordinator coordinator = new OrderCreateCoordinator(orderRepository, goodsQueryPort, stockPort,
                new DefaultOrderNoGenerator(clock, new AtomicInteger()::getAndIncrement), pipeline, properties, clock);

        // 支付的用例只走「读单 → 判超时 → 迁移 → 落库」，编排器只为构造齐备而存在（不参与这些路径）
        service = new OrderApplicationService(coordinator,
                new OrderCancelService(orderRepository, stockPort, statusFlow), orderRepository, statusFlow, clock);
    }

    // ── ① 没到期：照常支付 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("未到截止时刻 → 支付成功（判定不许误伤正常支付）")
    void payingBeforeDeadlineSucceeds() {
        OrderModel order = save(pendingOrder("202609211200000001", NOW));

        service.payOrder(order.getOrderNo(), payDto(TOTAL));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
    }

    @Test
    @DisplayName("截止时刻为 NULL 的历史单 → 无超时，仍可支付")
    void legacyOrderWithoutDeadlineIsStillPayable() {
        OrderModel legacy = save(legacyOrder("202609211200000002"));

        service.payOrder(legacy.getOrderNo(), payDto(TOTAL));

        assertThat(legacy.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    // ── ② 已过期：拒付，且不写库 ────────────────────────────────────────────────

    @Test
    @DisplayName("已过截止时刻 → 400 提示已过期，状态与轨迹都不动（本路径不关单，关单是任务的事）")
    void payingExpiredOrderIsRejected() {
        // 20 分钟前下单 → 10 分钟前就该付完 → 现在已过期
        OrderModel order = save(pendingOrder("202609211200000003", NOW.minusMinutes(20)));

        assertThatThrownBy(() -> service.payOrder(order.getOrderNo(), payDto(TOTAL)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("该订单已过期");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("到点即过期：截止时刻正好等于现在 → 拒付（与关单取数同一句闭区间）")
    void payingExactlyAtDeadlineIsRejected() {
        // 12:00 下单 → 12:10:00 到期；把时钟推到 12:10:00 那一刻
        OrderModel order = save(pendingOrder("202609211200000004", NOW));
        clock.advanceSeconds(600);

        assertThatThrownBy(() -> service.payOrder(order.getOrderNo(), payDto(TOTAL)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("该订单已过期");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("过期 + 金额也不对 → 结论是「已过期」（判定先于金额校验，否则顾客会以为改个金额还能付）")
    void expiryIsJudgedBeforeAmountCheck() {
        OrderModel order = save(pendingOrder("202609211200000005", NOW.minusHours(1)));

        assertThatThrownBy(() -> service.payOrder(order.getOrderNo(), payDto("0.01")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("该订单已过期");
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────────

    /**
     * 一笔**已落库**的待支付单（{@value #QUANTITY} 件 SKU_A、各 10.00 元；截止时刻 = 下单时刻 + 10 分钟）
     *
     * @param createTime 下单时刻（相对 {@link #NOW} 取：早于「现在减 10 分钟」即已过期、
     *                   等于 {@link #NOW} 则还没到期）
     */
    private OrderModel pendingOrder(String orderNo, LocalDateTime createTime) {
        OrderModel order = OrderModel.open(orderNo, CUSTOMER_ID, STORE_ID, "示例店铺", OrderSource.DIRECT,
                ADDRESS, "req-" + orderNo, "fp-" + orderNo, createTime, createTime.plusMinutes(10),
                List.of(new OrderLine(SKU_A, QUANTITY)));
        order.applyGoodsSnapshot(SKU_A, snapshot());
        order.applyPrice(SKU_A, new BigDecimal("10.00"));
        order.seal();
        return order;
    }

    /** 一笔截止时刻为 NULL 的历史单（{@code open} 拒 null，故只能从重建路径造出来） */
    private OrderModel legacyOrder(String orderNo) {
        OrderItem item = OrderItem.rehydrate(SKU_A, QUANTITY, 1000L + SKU_A, "商品" + SKU_A,
                "http://img/" + SKU_A + ".png", Map.of("颜色", "黑"), new BigDecimal("10.00"), new BigDecimal(TOTAL));
        return OrderModel.rehydrate(orderNo, CUSTOMER_ID, STORE_ID, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-" + orderNo, "fp-" + orderNo, null, NOW.minusHours(1), null, List.of(item),
                List.of(OrderStatus.PENDING_PAYMENT), statusFlow);
    }

    private OrderModel save(OrderModel order) {
        assertThat(stockPort.deduct(SKU_A, QUANTITY, order.getOrderNo())).isTrue();
        long submissionId = orderRepository.occupy(order.getCustomerId(), null).submissionId();
        orderRepository.saveAll(submissionId, List.of(order));
        return order;
    }

    private static TradeOrderPayDTO payDto(String amount) {
        TradeOrderPayDTO dto = new TradeOrderPayDTO();
        dto.setCustomerId(CUSTOMER_ID);
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }

    private static SkuSnapshot snapshot() {
        return new SkuSnapshot(2000L + SKU_A, SKU_A, STORE_ID, "示例店铺", "商品" + SKU_A,
                "http://img/" + SKU_A + ".png", Map.of("颜色", "黑"), new BigDecimal("10.00"), true, true, true, false);
    }
}
