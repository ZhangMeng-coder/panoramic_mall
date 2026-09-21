package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderFingerprint;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.StockOutboundRecord;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 拆单（裁定 D3）：一次提交按 `storeId` 拆成多笔，**一单一店**；各笔的金额、状态、扣减彼此独立。
 *
 * <p>⚠ 核心断言不是「笔数对」，而是「独立性」：多店订单如果共用了金额或状态，
 * 笔数照样是对的，但每一笔都算错了。故金额、状态轨迹、店铺名、指纹、单号、出库记录都要逐笔对账。</p>
 *
 * <p>⚠ 返回顺序按 `storeId` 升序是刻意的确定性（字典序无业务含义，但它让「同一批入参 → 同一个返回顺序」
 * 成立）：故有一条用例刻意把入参行顺序颠倒，验证返回顺序不受影响。</p>
 */
class OrderSplitTest {

    private static final Long STORE_A = 7L;
    private static final Long STORE_B = 8L;
    private static final Long SKU_A = 10L;
    private static final Long SKU_B = 20L;

    private MutableClock clock;
    private InMemoryGoodsQueryPort goodsQueryPort;
    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderCreateCoordinator coordinator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        goodsQueryPort = new InMemoryGoodsQueryPort();
        stockPort = new InMemoryStockPort(clock);
        orderRepository = new InMemoryOrderRepository();

        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_A, STORE_A, "一号店", "10.00"));
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_B, STORE_B, "二号店", "5.00"));
        stockPort.setStock(SKU_A, 5);
        stockPort.setStock(SKU_B, 5);

        OrderProperties properties = new OrderProperties();
        properties.setSteps(List.of(GoodsCheckStep.NAME, StockCheckStep.NAME, PriceComputeStep.NAME));
        properties.setStatusFlow(List.of(OrderStatus.values()));
        properties.setIdempotencyWindowSeconds(300);
        properties.setOrderNoMaxRetry(5);

        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goodsQueryPort), new StockCheckStep(stockPort), new PriceComputeStep(goodsQueryPort)),
                properties);
        AtomicInteger sequence = new AtomicInteger();
        coordinator = new OrderCreateCoordinator(orderRepository, goodsQueryPort, stockPort,
                new DefaultOrderNoGenerator(clock, sequence::getAndIncrement), pipeline, properties, clock);
    }

    @Test
    @DisplayName("多店购物车 → 两笔，按 storeId 升序，各店金额 / 店铺名 / 指纹 / 单号互不相同")
    void cartFromTwoStoresSplitsIntoTwoOrders() {
        List<OrderModel> created = coordinator.create(new OrderCreateCommand(11L, OrderSource.CART, "req-1",
                List.of(new OrderCreateCommand.Line(SKU_A, 2), new OrderCreateCommand.Line(SKU_B, 3))));

        assertThat(created).hasSize(2);

        OrderModel first = created.get(0);
        assertThat(first.getStoreId()).isEqualTo(STORE_A);
        assertThat(first.getStoreName()).isEqualTo("一号店");
        assertThat(first.getItems()).extracting(OrderItem::getSkuId).containsExactly(SKU_A);
        assertThat(first.getTotalQuantity()).isEqualTo(2);
        assertThat(first.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(first.getFingerprint()).isEqualTo(OrderFingerprint.of(
                11L, OrderSource.CART, STORE_A, List.of(new OrderLine(SKU_A, 2))));

        OrderModel second = created.get(1);
        assertThat(second.getStoreId()).isEqualTo(STORE_B);
        assertThat(second.getStoreName()).isEqualTo("二号店");
        assertThat(second.getItems()).extracting(OrderItem::getSkuId).containsExactly(SKU_B);
        assertThat(second.getTotalQuantity()).isEqualTo(3);
        assertThat(second.getTotalAmount()).isEqualByComparingTo("15.00");
        assertThat(second.getFingerprint()).isEqualTo(OrderFingerprint.of(
                11L, OrderSource.CART, STORE_B, List.of(new OrderLine(SKU_B, 3))));

        // 单号不同（同秒内靠序列位区分），且都落在仓库里
        assertThat(first.getOrderNo()).isNotEqualTo(second.getOrderNo());
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("各笔状态彼此独立：都是「待支付」且轨迹各自只有初始状态")
    void eachOrderHasItsOwnStatusTrail() {
        List<OrderModel> created = coordinator.create(new OrderCreateCommand(11L, OrderSource.CART, "req-1",
                List.of(new OrderCreateCommand.Line(SKU_A, 1), new OrderCreateCommand.Line(SKU_B, 1))));

        assertThat(created).allSatisfy(order -> {
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(order.getStatusTrail()).containsExactly(OrderStatus.PENDING_PAYMENT);
            assertThat(order.isSealed()).isTrue();
            assertThat(order.getCustomerId()).isEqualTo(11L);
            assertThat(order.getSource()).isEqualTo(OrderSource.CART);
            assertThat(order.getRequestId()).isEqualTo("req-1");
        });
    }

    @Test
    @DisplayName("各店库存各自扣：A 店扣 2、B 店扣 3，出库记录各一条且挂在各自的单号上")
    void stockIsDeductedPerStore() {
        List<OrderModel> created = coordinator.create(new OrderCreateCommand(11L, OrderSource.CART, "req-1",
                List.of(new OrderCreateCommand.Line(SKU_A, 2), new OrderCreateCommand.Line(SKU_B, 3))));

        assertThat(stockPort.available(SKU_A)).isEqualTo(3);
        assertThat(stockPort.available(SKU_B)).isEqualTo(2);
        assertThat(stockPort.outboundRecords()).hasSize(2);
        assertThat(stockPort.outboundRecords()).anySatisfy(record -> {
            assertThat(record.skuId()).isEqualTo(SKU_A);
            assertThat(record.quantity()).isEqualTo(2);
            assertThat(record.orderNo()).isEqualTo(created.get(0).getOrderNo());
        });
        assertThat(stockPort.outboundRecords()).anySatisfy(record -> {
            assertThat(record.skuId()).isEqualTo(SKU_B);
            assertThat(record.quantity()).isEqualTo(3);
            assertThat(record.orderNo()).isEqualTo(created.get(1).getOrderNo());
        });
    }

    @Test
    @DisplayName("入参行顺序颠倒不影响返回顺序（仍按 storeId 升序）")
    void returnOrderIsIndependentOfInputOrder() {
        List<OrderModel> created = coordinator.create(new OrderCreateCommand(11L, OrderSource.CART, "req-1",
                List.of(new OrderCreateCommand.Line(SKU_B, 1), new OrderCreateCommand.Line(SKU_A, 1))));

        assertThat(created).extracting(OrderModel::getStoreId).containsExactly(STORE_A, STORE_B);
    }

    @Test
    @DisplayName("单店 → 一笔（不因为多行而拆开）")
    void singleStoreStaysOneOrder() {
        List<OrderModel> created = coordinator.create(new OrderCreateCommand(11L, OrderSource.DIRECT, "req-1",
                List.of(new OrderCreateCommand.Line(SKU_A, 2))));

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getStoreId()).isEqualTo(STORE_A);
    }

    @Test
    @DisplayName("多店下某店商品不可购买 → 整次提交失败，另一店也不留痕（拆单不是「能拆多少算多少」）")
    void oneStoreFailingFailsTheWholeSubmission() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.locked(SKU_B, STORE_B, "二号店", "5.00"));

        assertThatThrownBy(() -> coordinator.create(
                        new OrderCreateCommand(11L, OrderSource.CART, "req-1",
                                List.of(new OrderCreateCommand.Line(SKU_A, 2), new OrderCreateCommand.Line(SKU_B, 1)))))
                .isInstanceOf(ServiceException.class)
                .hasMessage("商品已下架或不可购买");

        // ⚠ 一号店排在前面（storeId 升序），它**已经扣完库存**才轮到二号店炸：
        // 回补是「补偿」不是「擦除」，故出库流水里留下正负各一条（净 0），库存也确实回到了原值
        assertThat(stockPort.available(SKU_A)).isEqualTo(5);
        assertThat(stockPort.available(SKU_B)).isEqualTo(5);
        assertThat(stockPort.outboundRecords()).extracting(StockOutboundRecord::quantity).containsExactly(2, -2);
        assertThat(stockPort.outboundRecords().stream().mapToInt(StockOutboundRecord::quantity).sum()).isZero();
        assertThat(orderRepository.count()).isZero();   // 失败即整次不留单
    }
}
