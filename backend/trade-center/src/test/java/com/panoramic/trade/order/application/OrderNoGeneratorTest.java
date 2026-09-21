package com.panoramic.trade.order.application;

import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderNoGenerator;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 订单号：格式与定长、同秒内可区分，以及「生成 → 查重 → 重试 → 超限抛错」这条链（裁定 D5）。
 *
 * <p>⚠ 时钟与序列源可注入是这些断言的前提：单号里含时间，不固定时钟就只能断言「有个单号」；
 * 不注入序列就构造不出「撞车」与「超限」这两种必须验到的路径。</p>
 *
 * <p>⚠ 重试与超限虽然发生在编排层，用例放在这里：它们是**订单号这件事**的完整语义——
 * 生成器只管吐号，谁保证不重、试几次才放弃属于同一段口径，拆到两个文件里读起来就是两件事。</p>
 */
class OrderNoGeneratorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 这一刻在 Asia/Shanghai 是 2026-09-21 12:00:00 */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-21T04:00:00Z");
    private static final String EXPECTED_PREFIX = "20260921120000";

    private static final Long STORE_A = 7L;
    private static final Long STORE_B = 8L;
    private static final Long SKU_A = 10L;
    private static final Long SKU_B = 20L;

    private MutableClock clock;
    private InMemoryGoodsQueryPort goodsQueryPort;
    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderProperties properties;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(FIXED_INSTANT, ZONE);
        goodsQueryPort = new InMemoryGoodsQueryPort();
        stockPort = new InMemoryStockPort(clock);
        orderRepository = new InMemoryOrderRepository();
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_A, STORE_A, "一号店", "10.00"));
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_B, STORE_B, "二号店", "10.00"));
        stockPort.setStock(SKU_A, 5);
        stockPort.setStock(SKU_B, 5);

        properties = new OrderProperties();
        properties.setSteps(List.of(GoodsCheckStep.NAME, StockCheckStep.NAME, PriceComputeStep.NAME));
        properties.setStatusFlow(List.of(OrderStatus.values()));
        properties.setIdempotencyWindowSeconds(300);
        properties.setOrderNoMaxRetry(5);
    }

    private OrderCreateCoordinator coordinator(OrderNoGenerator generator) {
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goodsQueryPort), new StockCheckStep(stockPort), new PriceComputeStep(goodsQueryPort)),
                properties);
        return new OrderCreateCoordinator(orderRepository, goodsQueryPort, stockPort, generator, pipeline, properties, clock);
    }

    private static OrderCreateCommand command(String requestId, OrderCreateCommand.Line... lines) {
        return new OrderCreateCommand(11L, OrderSource.CART, requestId, List.of(lines));
    }

    private static OrderCreateCommand.Line line(Long skuId, int quantity) {
        return new OrderCreateCommand.Line(skuId, quantity);
    }

    /** 预置一笔「占了某个单号」的订单，用来构造单号冲突 */
    private void seedOrderWithOrderNo(String orderNo) {
        OrderModel seeded = OrderModel.open(orderNo, 11L, STORE_A, "一号店", OrderSource.CART,
                "req-seeded", "fp-seeded", LocalDateTime.now(clock), List.of(new OrderLine(SKU_B, 1)));
        SkuSnapshot snapshot = goodsQueryPort.mapBySkuIds(List.of(SKU_B)).get(SKU_B);
        seeded.applyGoodsSnapshot(SKU_B, snapshot);
        seeded.applyPrice(SKU_B, snapshot.price());
        seeded.seal();
        orderRepository.saveAll(List.of(seeded));
    }

    // ── 格式与长度 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("固定时钟 + 固定序列 → 单号完全可预测（yyyyMMddHHmmss + 4 位序列，共 18 位）")
    void numberIsPredictable() {
        OrderNoGenerator generator = new DefaultOrderNoGenerator(clock, () -> 7);

        String orderNo = generator.next();

        assertThat(orderNo).isEqualTo(EXPECTED_PREFIX + "0007");
        assertThat(orderNo).hasSize(18);
    }

    @Test
    @DisplayName("生产构造器（随机序列）同样给出 18 位、同一时间前缀")
    void randomSequenceKeepsFormat() {
        OrderNoGenerator generator = new DefaultOrderNoGenerator(Clock.fixed(FIXED_INSTANT, ZONE));

        String orderNo = generator.next();

        assertThat(orderNo).hasSize(18).startsWith(EXPECTED_PREFIX);
        assertThat(orderNo).matches("\\d{18}");
    }

    @Test
    @DisplayName("序列越界也不破坏定长：取模落在 0..9999（10007 → 0007，-1 → 9999）")
    void outOfRangeSequenceKeepsFixedLength() {
        assertThat(new DefaultOrderNoGenerator(clock, () -> 10007).next()).isEqualTo(EXPECTED_PREFIX + "0007");
        assertThat(new DefaultOrderNoGenerator(clock, () -> -1).next()).isEqualTo(EXPECTED_PREFIX + "9999");
        assertThat(new DefaultOrderNoGenerator(clock, () -> 0).next()).isEqualTo(EXPECTED_PREFIX + "0000");
    }

    @Test
    @DisplayName("同一秒内多次生成靠序列位区分：时间前缀相同、序列不同、长度不变")
    void sameSecondNumbersAreDistinguishedBySequence() {
        AtomicInteger sequence = new AtomicInteger();
        OrderNoGenerator generator = new DefaultOrderNoGenerator(clock, sequence::getAndIncrement);

        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            numbers.add(generator.next());
        }

        assertThat(numbers).containsExactly(
                EXPECTED_PREFIX + "0000", EXPECTED_PREFIX + "0001", EXPECTED_PREFIX + "0002");
        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(number -> assertThat(number).hasSize(18));
    }

    // ── 生成 → 查重 → 重试 → 超限 ──────────────────────────────────────────────

    @Test
    @DisplayName("单号已被占用 → 重试后换一个（原单号不用，新单号入库）")
    void conflictingOrderNoIsRetried() {
        seedOrderWithOrderNo("NO-DUP");
        ScriptedOrderNoGenerator generator = new ScriptedOrderNoGenerator(List.of("NO-DUP", "NO-OK"));

        List<OrderModel> created = coordinator(generator).create(command("req-1", line(SKU_A, 1)));

        assertThat(created).singleElement().satisfies(order -> assertThat(order.getOrderNo()).isEqualTo("NO-OK"));
        assertThat(generator.callCount()).isEqualTo(2);
        assertThat(orderRepository.existsByOrderNo("NO-OK")).isTrue();
        assertThat(orderRepository.count()).isEqualTo(2);   // 预置那笔 + 新建那笔
    }

    @Test
    @DisplayName("连续冲突超过重试上限 → IllegalStateException（不无限重试），且不留任何痕迹")
    void exhaustedRetryThrows() {
        seedOrderWithOrderNo("NO-DUP");
        properties.setOrderNoMaxRetry(3);
        ScriptedOrderNoGenerator generator = new ScriptedOrderNoGenerator(List.of("NO-DUP"));

        Throwable thrown = catchThrowable(() -> coordinator(generator).create(command("req-1", line(SKU_A, 1))));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage()).contains("3");
        assertThat(generator.callCount()).isEqualTo(3);      // 正好试了上限次
        assertThat(stockPort.available(SKU_A)).isEqualTo(5); // 生成单号在扣库存之前，故什么都没发生
        assertThat(stockPort.outboundRecords()).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(1);    // 只剩预置那笔
    }

    @Test
    @DisplayName("同一批拆出的两笔不能拿到同一个单号（只查仓库会漏——那两笔此刻都还没入库）")
    void orderNoMustBeUniqueWithinTheSameBatch() {
        ScriptedOrderNoGenerator generator = new ScriptedOrderNoGenerator(List.of("NO-A", "NO-A", "NO-B"));

        List<OrderModel> created = coordinator(generator).create(
                command("req-1", line(SKU_A, 1), line(SKU_B, 1)));

        assertThat(created).extracting(OrderModel::getOrderNo).containsExactly("NO-A", "NO-B");
        assertThat(generator.callCount()).isEqualTo(3);     // 第 2 笔试了两次才拿到可用的号
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    /** 按脚本吐单号的假生成器（脚本用完后就一直吐最后一个，便于构造「永远冲突」） */
    private static final class ScriptedOrderNoGenerator implements OrderNoGenerator {

        private final List<String> script;
        private int calls;

        private ScriptedOrderNoGenerator(List<String> script) {
            this.script = List.copyOf(script);
        }

        @Override
        public String next() {
            String value = script.get(Math.min(calls, script.size() - 1));
            calls++;
            return value;
        }

        private int callCount() {
            return calls;
        }
    }
}
