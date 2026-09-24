package com.panoramic.trade.order.application;

import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.support.InMemoryStockPort;
import com.panoramic.trade.order.support.OrderStatusChain;
import com.panoramic.trade.order.support.StockOutboundRecord;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 超时自动关单任务：**捞哪些单、逐笔关到什么程度、单笔失败会不会拖垮整批**。
 *
 * <p>⚠ 本类守的是四条口径，每条写错的后果都不会自己暴露：</p>
 * <ol>
 *   <li><b>只捞「仍停在待支付」且「已到截止时刻」的单</b>：把未到期的关掉是凭空吃掉顾客的付款时间；
 *       把已支付的关掉是一次注定失败的尝试（状态机会拒，但每轮都白跑一遍）；</li>
 *   <li><b>截止时刻为 NULL 的老单永远不关</b>（本列上线前的历史行，语义是「无超时」）——
 *       漏了这一条，任务上线的第一轮就会把库里所有老单一并关掉；</li>
 *   <li><b>关单是完整动作</b>：状态到「已取消」**且**库存回补（只做一半就是少卖或超卖）；</li>
 *   <li><b>单笔失败继续下一笔</b>：一批里最可能失败的恰恰是「刚被顾客付掉」的那笔，
 *       若它中断整批，只要有顾客在付款，超时单就永远关不干净；
 *       ⚠ 且失败还要**分两桶**（域侧拒 = warn 不带堆栈 / 系统错 = error 带堆栈，末尾汇总同级别）
 *       ——混成一桶时，「一轮全被顾客抢先付款」与「一轮全挂」在日志上长得一模一样。</li>
 * </ol>
 *
 * <p>⚠ 不启 Spring（{@code @Scheduled} 不参与本类）：直接调任务方法就是「一轮扫描」。
 * 真容器里的周期触发由 {@code @EnableScheduling} 负责，那属于装配层的事。</p>
 */
class OrderTimeoutCloseTaskTest {

    /** 固定的「现在」：2026-09-21T04:00Z = 上海时间 12:00（时钟口径与生产一致：系统默认时区） */
    private static final Instant NOW_INSTANT = Instant.parse("2026-09-21T04:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    private static final Long CUSTOMER_ID = 11L;
    private static final Long STORE_ID = 7L;
    private static final Long SKU_A = 10L;
    private static final int QUANTITY = 2;
    private static final int STOCK = 20;

    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private MutableClock clock;
    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderStatusFlow statusFlow;
    private OrderProperties properties;

    /** 本任务的 logger（`@Slf4j` 生成的那个）—— 两类失败只在日志上分得开，故断言日志 */
    private static final Logger TASK_LOGGER =
            (Logger) LoggerFactory.getLogger(OrderTimeoutCloseTask.class);

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW_INSTANT, ZoneId.of("Asia/Shanghai"));
        stockPort = new InMemoryStockPort(clock);
        stockPort.setStock(SKU_A, STOCK);
        orderRepository = new InMemoryOrderRepository();
        statusFlow = new OrderStatusFlow(OrderStatusChain.production());
        properties = new OrderProperties();
        properties.setTimeoutCloseBatchSize(200);
    }

    // ── ① 只关已到期的那一笔 ────────────────────────────────────────────────────

    @Test
    @DisplayName("一轮扫描只关「已过截止时刻」的那一笔：状态到已取消、库存回补，未到期的一笔原样不动")
    void closesOnlyExpiredOrders() {
        OrderModel expired = save(pendingOrder("202609211200000001", NOW.minusMinutes(20)));   // 11:40 下单 → 11:50 到期
        OrderModel alive = save(pendingOrder("202609211200000002", NOW.minusMinutes(5)));     // 11:55 下单 → 12:05 到期
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2 * QUANTITY);

        task(stockPort).closeTimeoutOrders();

        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
        assertThat(alive.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        // 库存：只还了被关掉那一笔的 2 件，未到期那笔仍占着货架
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);
        assertThat(netOutbound()).isEqualTo(QUANTITY);
        // 流水顺序 = 两笔下单各扣一次，随后被关的那笔回补一次（正数出库、负数回补）
        assertThat(stockPort.outboundRecords()).extracting(StockOutboundRecord::orderNo, StockOutboundRecord::quantity)
                .containsExactly(tuple(expired.getOrderNo(), QUANTITY),
                        tuple(alive.getOrderNo(), QUANTITY),
                        tuple(expired.getOrderNo(), -QUANTITY));
    }

    @Test
    @DisplayName("截止时刻为 NULL 的老单永不超时：一轮扫描它也捞不到、一笔都不动")
    void ordersWithoutDeadlineAreNeverClosed() {
        // 本列上线前的历史行：expire_time 为 NULL，语义是「无超时」（rehydrate 是唯一能造出这种单的入口）
        OrderModel legacy = save(legacyOrder("202609211200000003", NOW.minusHours(3)));

        task(stockPort).closeTimeoutOrders();

        assertThat(legacy.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(legacy.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT);
        // 连库存都没动过（没有回补流水、货架上仍占着它的那 2 件）
        assertThat(stockPort.outboundRecords()).hasSize(1);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);
    }

    @Test
    @DisplayName("到点即过期：截止时刻正好等于现在的那一笔会被关掉（闭区间，与域内判据同一句）")
    void orderExpiringExactlyNowIsClosed() {
        // 12:00 下单的 10 分钟时限 → 12:10:00 到期；把时钟推到 12:10:00 那一刻
        OrderModel order = save(pendingOrder("202609211200000004", NOW));
        clock.advanceSeconds(600);
        assertThat(LocalDateTime.now(clock)).isEqualTo(NOW.plusMinutes(10));

        task(stockPort).closeTimeoutOrders();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ── ② 单笔失败不拖垮整批 ────────────────────────────────────────────────────

    @Test
    @DisplayName("先到期的那笔关单失败（库存回补抛错）→ 后面那一笔照关，整批不中断")
    void oneFailingOrderDoesNotStopTheBatch() {
        OrderModel failing = save(pendingOrder("202609211200000005", NOW.minusMinutes(30)));  // 先到期
        OrderModel next = save(pendingOrder("202609211200000006", NOW.minusMinutes(20)));     // 后到期

        // 让「先到期」那一笔的回补炸掉：模拟库存在 store 域写失败（跨服务写失败的那一类）
        StockPort failingRevert = new FailingRevertStockPort(stockPort, failing.getOrderNo());
        ListAppender<ILoggingEvent> logs = captureLogs();

        Throwable thrown = catchThrowable(() -> task(failingRevert).closeTimeoutOrders());

        // 任务自己不抛（单笔失败只记日志）：抛出就等于这一批剩下的单永远轮不到
        assertThat(thrown).isNull();
        assertThat(next.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);   // 只有失败那笔没还

        // ⚠ 系统错必须与「域侧拒」分得开：error 级、**带堆栈**（域侧拒那条不带），末尾汇总也升到 error
        List<ILoggingEvent> errors = eventsAt(logs, Level.ERROR);
        assertThat(errors).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("超时关单失败（系统错"))
                .anySatisfy(message -> assertThat(message).contains("失败 1 笔"));
        assertThat(errors).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNotNull());
        assertThat(eventsAt(logs, Level.WARN)).isEmpty();   // 这一批没有「域侧拒」，不该有 warn
    }

    @Test
    @DisplayName("捞到之后、关单之前顾客把钱付了 → 域侧拒（400）记 warn 且不带堆栈，单与库存都不动")
    void orderPaidBetweenScanAndCloseIsSkipped() {
        OrderModel order = save(pendingOrder("202609211200000009", NOW.minusMinutes(30)));
        // 模拟真实竞态：先取数（那一刻它还是超时待支付），随后顾客把钱付掉，任务才轮到关它
        List<OrderModel> scanned = List.copyOf(
                orderRepository.findTimeoutPending(NOW, properties.getTimeoutCloseBatchSize()));
        order.markPaid(statusFlow, order.getTotalAmount());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        ListAppender<ILoggingEvent> logs = captureLogs();

        task(new StaleScanRepository(scanned), stockPort).closeTimeoutOrders();

        // 这单不该被关：状态与轨迹停在付款那一刻，库存也没被还回去
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);
        // ⚠ 判据只在日志上：域侧拒 → warn（**不带堆栈**，那是噪音）、汇总记「跳过」而不是「失败」
        List<ILoggingEvent> warnings = eventsAt(logs, Level.WARN);
        assertThat(warnings).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("超时关单跳过（域侧拒绝）"));
        assertThat(warnings).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        assertThat(eventsAt(logs, Level.INFO)).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("跳过 1 笔"));
        assertThat(eventsAt(logs, Level.ERROR)).isEmpty();   // 域侧拒不是故障，不该出现 error
    }

    @Test
    @DisplayName("已被关掉的那一笔进不了这一轮的取数集（任务幂等）→ 只关剩下那笔，且不会重复回补")
    void alreadyClosedOrderIsNotPickedUpAgain() {
        OrderModel first = save(pendingOrder("202609211200000007", NOW.minusMinutes(30)));
        OrderModel second = save(pendingOrder("202609211200000008", NOW.minusMinutes(25)));
        // 先自己关掉第一笔（等价于顾客主动取消抢先，或上一轮任务关过它）
        new OrderCancelService(orderRepository, stockPort, statusFlow).cancel(first);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - QUANTITY);

        task(stockPort).closeTimeoutOrders();

        assertThat(second.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // 两笔都还清了，且没有第二遍回补（若「已取消」的单又被捞出来关一次，这里会变成 STOCK + 2）
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(stockPort.outboundRecords()).hasSize(4);
    }

    // ── ③ 装配期护栏 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("单批上限配成 0 → 装配期即失败（否则每轮都捞不到单、任务静默失效）")
    void nonPositiveBatchSizeRejectedAtWiring() {
        OrderProperties bad = new OrderProperties();
        bad.setTimeoutCloseBatchSize(0);

        assertThatThrownBy(() -> new OrderTimeoutCloseTask(orderRepository,
                new OrderTimeoutCloseService(orderRepository, new OrderCancelService(orderRepository, stockPort, statusFlow)),
                bad, clock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("单批上限必须是正数");
    }

    // ── 夹具 ────────────────────────────────────────────────────────────────────

    private OrderTimeoutCloseTask task(StockPort stock) {
        return task(orderRepository, stock);
    }

    /** 换仓储时的入口：任务与关单服务必须拿**同一个**仓储（两处不一致就不是同一条读路径） */
    private OrderTimeoutCloseTask task(OrderRepository repository, StockPort stock) {
        return new OrderTimeoutCloseTask(repository,
                new OrderTimeoutCloseService(repository, new OrderCancelService(repository, stock, statusFlow)),
                properties, clock);
    }

    /** 把内存 appender 挂到本任务的 logger 上，返回它以便断言（用例之间互不影响：各自挂各自读） */
    private static ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        TASK_LOGGER.addAppender(appender);
        return appender;
    }

    private static List<ILoggingEvent> eventsAt(ListAppender<ILoggingEvent> appender, Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    /**
     * 一笔**已落库**的待支付单（{@value #QUANTITY} 件 SKU_A、各 10.00 元；截止时刻 = 下单时刻 + 10 分钟）
     *
     * @param createTime 下单时刻（相对 {@link #NOW} 取，决定它是已到期还是还没到）
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

    /**
     * 一笔**截止时刻为 NULL** 的历史单（重建路径是唯一能造出它的入口：{@code open} 会拒 null）
     */
    private OrderModel legacyOrder(String orderNo, LocalDateTime createTime) {
        OrderItem item = OrderItem.rehydrate(SKU_A, QUANTITY, 1000L + SKU_A, "商品" + SKU_A,
                "http://img/" + SKU_A + ".png", Map.of("颜色", "黑"), new BigDecimal("10.00"), new BigDecimal("20.00"));
        return OrderModel.rehydrate(orderNo, CUSTOMER_ID, STORE_ID, "示例店铺", OrderSource.DIRECT, ADDRESS,
                "req-" + orderNo, "fp-" + orderNo, null, createTime, null, List.of(item),
                List.of(OrderStatus.PENDING_PAYMENT), statusFlow);
    }

    /** 落库 + 扣库存（扣库存这一步让「回补」在流水上可观察） */
    private OrderModel save(OrderModel order) {
        assertThat(stockPort.deduct(SKU_A, QUANTITY, order.getOrderNo())).isTrue();
        long submissionId = orderRepository.occupy(order.getCustomerId(), null).submissionId();
        orderRepository.saveAll(submissionId, List.of(order));
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

    /** 对指定单号的回补一律失败，其余照常转发的库存端口：验「单笔失败不拖垮整批」 */
    private static final class FailingRevertStockPort implements StockPort {

        private final StockPort delegate;
        private final String failingOrderNo;

        private FailingRevertStockPort(StockPort delegate, String failingOrderNo) {
            this.delegate = delegate;
            this.failingOrderNo = failingOrderNo;
        }

        @Override
        public boolean deduct(Long skuId, int quantity, String orderNo) {
            return delegate.deduct(skuId, quantity, orderNo);
        }

        @Override
        public void revertByOrder(String orderNo) {
            if (failingOrderNo.equals(orderNo)) {
                throw new IllegalStateException("回补失败：库存服务不可用");
            }
            delegate.revertByOrder(orderNo);
        }

        @Override
        public int available(Long skuId) {
            return delegate.available(skuId);
        }
    }

    /**
     * 「取数快照停在关单之前」的仓储：{@code findTimeoutPending} 永远返回预先捕获的那一批，其余全委托。
     *
     * <p>用来演「捞到之后、关单之前顾客把钱付了」这条真实竞态——内存实现存的是**活引用**，
     * 顾客付款改的就是同一个对象，故只有把取数结果先钉住，才重现得出「任务拿到的还是旧批次」。</p>
     */
    private static final class StaleScanRepository extends InMemoryOrderRepository {

        private final List<OrderModel> staleBatch;

        private StaleScanRepository(List<OrderModel> staleBatch) {
            this.staleBatch = staleBatch;
        }

        @Override
        public List<OrderModel> findTimeoutPending(LocalDateTime now, int limit) {
            return staleBatch;
        }
    }
}
