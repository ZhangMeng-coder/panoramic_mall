package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderFingerprint;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 下单编排（happy path 与拒绝分支）：一店一笔、初始状态「待支付」、金额与库存正确、
 * 入参合并与校验、goods-check 的四条拒绝分支都不留痕。
 *
 * <p>⚠ 本类**不启 Spring**（裁定 D10）：编排的输入输出全是内存端口 + 本体模型，
 * 用真步骤（{@code GoodsCheckStep}/{@code StockCheckStep}/{@code PriceComputeStep}）+ 内存适配器
 * 就能把行为完整跑出来；起上下文只会让失败原因在「装配」与「行为」之间摇摆。</p>
 */
class OrderCreateCoordinatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant BASE_INSTANT = Instant.parse("2026-09-21T04:00:00Z");

    private MutableClock clock;
    private InMemoryGoodsQueryPort goodsQueryPort;
    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderProperties properties;
    private OrderCreateCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(BASE_INSTANT, ZONE);
        goodsQueryPort = new InMemoryGoodsQueryPort();
        stockPort = new InMemoryStockPort(clock);
        orderRepository = new InMemoryOrderRepository();
        properties = defaultProperties();
        coordinator = coordinatorUsing(goodsQueryPort);
    }

    private OrderCreateCoordinator coordinatorUsing(GoodsQueryPort goodsPort) {
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goodsPort), new StockCheckStep(stockPort), new PriceComputeStep(goodsPort)), properties);
        // 单号用真实生成器 + 递增序列：单号可预测，且同批多笔天然互不相同
        AtomicInteger sequence = new AtomicInteger();
        return new OrderCreateCoordinator(orderRepository, goodsPort, stockPort,
                new DefaultOrderNoGenerator(clock, sequence::getAndIncrement), pipeline, properties, clock);
    }

    private static OrderProperties defaultProperties() {
        OrderProperties props = new OrderProperties();
        props.setSteps(List.of(GoodsCheckStep.NAME, StockCheckStep.NAME, PriceComputeStep.NAME));
        props.setStatusFlow(List.of(OrderStatus.values()));
        props.setIdempotencyWindowSeconds(300);
        props.setOrderNoMaxRetry(5);
        return props;
    }

    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private static OrderCreateCommand command(OrderSource source, String requestId, OrderCreateCommand.Line... lines) {
        return new OrderCreateCommand(11L, source, ADDRESS, requestId, List.of(lines));
    }

    private static OrderCreateCommand.Line line(Long skuId, int quantity) {
        return new OrderCreateCommand.Line(skuId, quantity);
    }

    // ── 入参：同 SKU 合并（D14）与区间校验 ──────────────────────────────────────

    @Test
    @DisplayName("同一 skuId 出现多行 → 合并数量，不报错（D14）")
    void duplicateSkuIdsAreMerged() {
        OrderCreateCommand command = command(OrderSource.CART, "req-1", line(10L, 1), line(20L, 3), line(10L, 2));

        assertThat(command.lines()).containsExactly(line(10L, 3), line(20L, 3));
    }

    @Test
    @DisplayName("合并后按 skuId 升序（入参顺序不同也得到同一个命令对象）")
    void mergedLinesAreSorted() {
        assertThat(command(OrderSource.CART, "r", line(30L, 1), line(10L, 1)).lines())
                .extracting(OrderCreateCommand.Line::skuId).containsExactly(10L, 30L);
    }

    @Test
    @DisplayName("合并后仍须落在 1..999：999+1 → 400，且消息点出是哪个 SKU")
    void mergedQuantityOutOfRangeRejected() {
        Throwable thrown = catchThrowable(() -> command(OrderSource.CART, "r", line(10L, 999), line(10L, 1)));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) thrown).getCode()).isEqualTo(400);
        assertThat(thrown.getMessage()).contains("10").contains("1000");
    }

    @Test
    @DisplayName("数量为 0 / 负数 → 400（0 件不是订单行）")
    void zeroQuantityRejected() {
        assertThatThrownBy(() -> command(OrderSource.DIRECT, "r", line(10L, 0)))
                .isInstanceOf(ServiceException.class).hasMessageContaining("1..999");
        assertThatThrownBy(() -> command(OrderSource.DIRECT, "r", line(10L, -1)))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("空行 / null → 400「下单商品不能为空」（空车不该变成一笔空订单）")
    void emptyLinesRejected() {
        assertThatThrownBy(() -> new OrderCreateCommand(11L, OrderSource.CART, ADDRESS, "r", List.of()))
                .isInstanceOf(ServiceException.class).hasMessageContaining("下单商品不能为空");
        assertThatThrownBy(() -> new OrderCreateCommand(11L, OrderSource.CART, ADDRESS, "r", null))
                .isInstanceOf(ServiceException.class).hasMessageContaining("下单商品不能为空");
    }

    // ── 商品校验的四条拒绝分支（一条都不能少） ──────────────────────────────────

    @Test
    @DisplayName("商品在商品域查不到 → 400「商品不存在」，且不留任何痕迹")
    void unknownSkuRejected() {
        stockPort.setStock(10L, 5);

        Throwable thrown = catchThrowable(() -> coordinator.create(command(OrderSource.DIRECT, "req-1", line(10L, 1))));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) thrown).getCode()).isEqualTo(400);
        assertThat(thrown.getMessage()).isEqualTo("商品不存在");
        assertNothingHappened(5);
    }

    @Test
    @DisplayName("店铺未审核 → 400「商品已下架或不可购买」")
    void shopNotApprovedRejected() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.shopNotApproved(10L, 7L, "示例店铺", "10.00"));
        stockPort.setStock(10L, 5);

        Throwable thrown = catchThrowable(() -> coordinator.create(command(OrderSource.DIRECT, "req-1", line(10L, 1))));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).isEqualTo("商品已下架或不可购买");
        assertNothingHappened(5);
    }

    @Test
    @DisplayName("SPU 已下架 → 400「商品已下架或不可购买」")
    void spuOffShelfRejected() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.spuOffShelf(10L, 7L, "示例店铺", "10.00"));
        stockPort.setStock(10L, 5);

        assertThatThrownBy(() -> coordinator.create(command(OrderSource.DIRECT, "req-1", line(10L, 1))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("商品已下架或不可购买");
        assertNothingHappened(5);
    }

    @Test
    @DisplayName("SKU 已下架 → 400「商品已下架或不可购买」")
    void skuOffShelfRejected() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.skuOffShelf(10L, 7L, "示例店铺", "10.00"));
        stockPort.setStock(10L, 5);

        assertThatThrownBy(() -> coordinator.create(command(OrderSource.DIRECT, "req-1", line(10L, 1))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("商品已下架或不可购买");
        assertNothingHappened(5);
    }

    @Test
    @DisplayName("被平台锁定 → 400「商品已下架或不可购买」（锁定是平台行为，与店主下架分开判）")
    void lockedSpuRejected() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.locked(10L, 7L, "示例店铺", "10.00"));
        stockPort.setStock(10L, 5);

        assertThatThrownBy(() -> coordinator.create(command(OrderSource.DIRECT, "req-1", line(10L, 1))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("商品已下架或不可购买");
        assertNothingHappened(5);
    }

    /** 被拒不等于「做了一半」：库存没动、流水没有、仓库没有 */
    private void assertNothingHappened(int expectedStock) {
        assertThat(stockPort.available(10L)).isEqualTo(expectedStock);
        assertThat(stockPort.outboundRecords()).isEmpty();
        assertThat(orderRepository.count()).isZero();
    }

    // ── happy path ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("一店一笔：待支付、已封存、金额与数量正确、库存已扣、出库记录一条、仓库有单")
    void happyPathCreatesOneOrder() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(10L, 7L, "示例店铺", "10.00"));
        stockPort.setStock(10L, 5);

        List<OrderModel> created = coordinator.create(command(OrderSource.DIRECT, "req-1", line(10L, 2)));

        assertThat(created).hasSize(1);
        OrderModel order = created.get(0);
        assertThat(order.getStoreId()).isEqualTo(7L);
        assertThat(order.getStoreName()).isEqualTo("示例店铺");
        assertThat(order.getSource()).isEqualTo(OrderSource.DIRECT);
        assertThat(order.getCustomerId()).isEqualTo(11L);
        assertThat(order.getRequestId()).isEqualTo("req-1");
        assertThat(order.getCreateTime()).isEqualTo(LocalDateTime.now(clock));
        assertThat(order.getOrderNo()).hasSize(18);
        assertThat(order.getFingerprint())
                .isEqualTo(OrderFingerprint.of(11L, OrderSource.DIRECT, 7L, List.of(new OrderLine(10L, 2))));

        // 状态：open 时即「待支付」，编排层不得再迁移一次（否则轨迹里会有两个初始状态）
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT);
        assertThat(order.isSealed()).isTrue();

        assertThat(order.getTotalQuantity()).isEqualTo(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSkuId()).isEqualTo(10L);
            assertThat(item.getSpuId()).isEqualTo(1010L);
            assertThat(item.getGoodsName()).isEqualTo("商品10");
            assertThat(item.getMainImage()).isEqualTo("http://img/10.png");
            assertThat(item.getSpecAttrs()).containsEntry("颜色", "黑");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("10.00");
            assertThat(item.getSubtotal()).isEqualByComparingTo("20.00");
        });

        assertThat(stockPort.available(10L)).isEqualTo(3);
        assertThat(stockPort.outboundRecords()).singleElement().satisfies(record -> {
            assertThat(record.skuId()).isEqualTo(10L);
            assertThat(record.quantity()).isEqualTo(2);
            assertThat(record.orderNo()).isEqualTo(order.getOrderNo());
        });

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderRepository.all()).containsExactly(order);
        // 第一级幂等的凭证是**本次提交落下的那一批**（含复用笔），不是「按 requestId 查订单行」：
        // 一批里可能有指纹命中的复用笔，它们的 requestId 是上一次提交的，按行查会少返回几笔。
        assertThat(orderRepository.findCommittedBatch(11L, "req-1").orElseThrow()).containsExactly(order);
    }

    @Test
    @DisplayName("同一店多行 → 仍是一笔（一单一店，不是一 SKU 一单）")
    void multipleSkuIdsInOneStoreStayOneOrder() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(10L, 7L, "示例店铺", "10.00"));
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(20L, 7L, "示例店铺", "5.00"));
        stockPort.setStock(10L, 5);
        stockPort.setStock(20L, 5);

        List<OrderModel> created = coordinator.create(command(OrderSource.CART, "req-1", line(10L, 1), line(20L, 2)));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getItems()).extracting(OrderItem::getSkuId).containsExactly(10L, 20L);
        assertThat(created.get(0).getTotalQuantity()).isEqualTo(3);
        assertThat(created.get(0).getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(stockPort.outboundRecords()).hasSize(2);
    }

    @Test
    @DisplayName("requestId 为 null 也能下单（第一级幂等不生效，不因此拒绝）")
    void nullRequestIdAllowed() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(10L, 7L, "示例店铺", "10.00"));
        stockPort.setStock(10L, 5);

        List<OrderModel> created = coordinator.create(command(OrderSource.DIRECT, null, line(10L, 1)));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getRequestId()).isNull();
        // requestId 为空 → 不记幂等映射（这次提交不做请求级去重），但订单照常落库
        assertThat(orderRepository.findCommittedBatch(11L, null)).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(1);
    }
}

/**
 * 可推进的测试时钟：幂等窗口的边界（窗口内复用 / 窗口外新单）只能靠「推进时间」来验，
 * 固定时钟做不到，系统时钟又不可预测。
 *
 * <p>⚠ 它与 {@code OrderCreateCoordinatorTest} 同文件：应用层的多个用例都要推进时钟，
 * 而本批次的交付清单是固定的文件集合（不另开文件），故共用一个包内可见的辅助类。</p>
 */
final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /**
     * 推进时钟（只前进，不后退：回退时间去测「窗口内」是自欺欺人）
     *
     * @param seconds 推进的秒数
     */
    void advanceSeconds(long seconds) {
        this.instant = this.instant.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
