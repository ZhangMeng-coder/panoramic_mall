package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.domain.port.StockOutboundRecord;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 失败回滚（裁定 D4/D13）：任一笔出错 → **整次提交**不留痕——库存回补、追加反向出库记录、不落单。
 *
 * <p>⚠ 三条最容易写错、也最难事后发现的口径，各有一条用例钉住：</p>
 * <ol>
 *   <li><b>没扣过的行不回补</b>：失败可能发生在 stock-check **之前/之中**（商品下架、第 2 行库存不足），
 *       那时前几行已扣、当行没扣。盲目按订单行逐行回补会把没扣过的也「还」回去——库存被冲多，
 *       且不会让任何一次下单失败（超卖的镜像错误）。</li>
 *   <li><b>复用笔不回补</b>：指纹命中的那一笔属于**上一次提交**，库存上次就扣过了；
 *       对它回补等于把上次真实下单占用的库存还回货架（多店场景下第 1 笔复用、第 2 笔失败即触发）。</li>
 *   <li><b>主异常不被吞</b>：回补过程中的次生错误只挂 suppressed，抛出去仍是原始异常——
 *       4xx 要能原样透传给页面，5xx 要能照常计入熔断（cross-cutting 第 13 条）。</li>
 * </ol>
 *
 * <p>⚠ 本类**不启 Spring**（裁定 D10）：失败注入靠内存端口的小包装即可，不需要容器。</p>
 */
class OrderRollbackTest {

    private static final Long STORE_A = 7L;
    private static final Long STORE_B = 8L;
    private static final Long SKU_A = 10L;
    private static final Long SKU_B = 20L;
    private static final Long SKU_B2 = 21L;
    private static final int STOCK = 5;

    private MutableClock clock;
    private InMemoryGoodsQueryPort goodsQueryPort;
    private InMemoryStockPort stockPort;
    private InMemoryOrderRepository orderRepository;
    private OrderProperties properties;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        goodsQueryPort = new InMemoryGoodsQueryPort();
        stockPort = new InMemoryStockPort(clock);
        orderRepository = new InMemoryOrderRepository();
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_A, STORE_A, "一号店", "10.00"));
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_B, STORE_B, "二号店", "10.00"));
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_B2, STORE_B, "二号店", "10.00"));
        stockPort.setStock(SKU_A, STOCK);
        stockPort.setStock(SKU_B, STOCK);
        stockPort.setStock(SKU_B2, 0);

        properties = new OrderProperties();
        properties.setSteps(List.of(GoodsCheckStep.NAME, StockCheckStep.NAME, PriceComputeStep.NAME));
        properties.setStatusFlow(List.of(OrderStatus.values()));
        properties.setIdempotencyWindowSeconds(300);
        properties.setOrderNoMaxRetry(5);
    }

    /**
     * 单号序列**跨协调器共享**（字段持有，不每次新建）
     *
     * <p>⚠ 这不是「顺手共享」：单号在真实系统里全局唯一，而失败提交**不落任何单**，
     * 故下一次提交在同一个时钟秒里完全可能拿到同一个号（重试/补偿的常规情形）。
     * 若每次新建协调器就重置序列，两个协调器会吐出同一个单号，
     * 而库存端口的回补是按 {@code orderNo + skuId} 去重的——第二条回补会被静默当成重复调用吃掉，
     * 用例里就会看到「库存少了 1 件」这种与本用例无关的假失败。</p>
     */
    private final AtomicInteger orderNoSequence = new AtomicInteger();

    private OrderCreateCoordinator coordinator(GoodsQueryPort goods, StockPort stock) {
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goods), new StockCheckStep(stock), new PriceComputeStep(goods)), properties);
        return new OrderCreateCoordinator(orderRepository, goods, stock,
                new DefaultOrderNoGenerator(clock, orderNoSequence::getAndIncrement), pipeline, properties, clock);
    }

    private static OrderCreateCommand command(String requestId, OrderCreateCommand.Line... lines) {
        return new OrderCreateCommand(11L, OrderSource.CART, requestId, List.of(lines));
    }

    private static OrderCreateCommand.Line line(Long skuId, int quantity) {
        return new OrderCreateCommand.Line(skuId, quantity);
    }

    /** 出库流水的净量：正数出库、负数回补，「净出库 0」就等于「库存回到原值」 */
    private int netOutbound() {
        return stockPort.outboundRecords().stream().mapToInt(StockOutboundRecord::quantity).sum();
    }

    // ── ① 扣完之后才失败：整笔回补 ──────────────────────────────────────────────

    @Test
    @DisplayName("stock-check 通过后 price-compute 失败 → 库存回补到原值、净出库 0、仓库无单、requestId 未被占用")
    void failureAfterStockCheckRollsBackEverything() {
        // 第 1 次查询=编排分组、第 2 次=goods-check、第 3 次=price-compute（本用例要它在第 3 次炸）
        OrderCreateCoordinator coordinator = coordinator(new FailingGoodsQueryPort(goodsQueryPort, 3), stockPort);

        Throwable thrown = catchThrowable(() -> coordinator.create(command("req-1", line(SKU_A, 2))));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage()).isEqualTo("商品域暂不可用");

        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(stockPort.outboundRecords()).hasSize(2);
        assertThat(netOutbound()).isZero();
        // 反向记录与正向记录挂在同一个订单号上，且数量互为相反数
        List<StockOutboundRecord> records = stockPort.outboundRecords();
        assertThat(records).extracting(StockOutboundRecord::quantity).containsExactly(2, -2);
        assertThat(records).allSatisfy(record -> assertThat(record.orderNo()).isEqualTo(records.get(0).orderNo()));

        assertThat(orderRepository.count()).isZero();
        assertThat(orderRepository.findByRequestId("req-1")).isEmpty();
    }

    // ── ② 多店：前一笔（本次新建）也被回补，没扣过的行不留记录 ──────────────────

    @Test
    @DisplayName("多店第 2 笔失败 → 第 1 笔（本次新建）也被回补；第 2 笔里没扣成的行不留记录")
    void oneOrderFailingRollsBackTheOtherNewOnes() {
        OrderCreateCoordinator coordinator = coordinator(goodsQueryPort, stockPort);

        Throwable thrown = catchThrowable(() -> coordinator.create(
                command("req-1", line(SKU_A, 2), line(SKU_B, 1), line(SKU_B2, 1))));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(((ServiceException) thrown).getCode()).isEqualTo(400);
        assertThat(thrown.getMessage()).isEqualTo("库存不足");

        // 一号店那笔（先跑、已扣）被回补；二号店扣成的 SKU 也被回补；没扣成的 SKU 什么都没发生
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK);
        assertThat(stockPort.available(SKU_B2)).isZero();
        assertThat(netOutbound()).isZero();
        assertThat(stockPort.outboundRecords()).extracting(StockOutboundRecord::skuId)
                .containsExactly(SKU_A, SKU_B, SKU_B, SKU_A);   // 正序扣、逆序还
        assertThat(orderRepository.count()).isZero();
    }

    // ── ③ 复用笔不被回补（两级幂等 × 整次回滚的交汇点） ────────────────────────

    @Test
    @DisplayName("多店：第 1 笔复用、第 2 笔新建后失败 → 复用笔的库存不被回补，新建笔的回补")
    void reusedOrderIsNeverRolledBack() {
        OrderCreateCoordinator coordinator = coordinator(goodsQueryPort, stockPort);
        List<OrderModel> firstBatch = coordinator.create(command("req-1", line(SKU_A, 2)));
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);

        // 第二次提交：一号店指纹命中（复用首单）、二号店新建，而二号店第 2 个 SKU 库存不足
        Throwable thrown = catchThrowable(() -> coordinator.create(
                command("req-2", line(SKU_A, 2), line(SKU_B, 1), line(SKU_B2, 1))));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).isEqualTo("库存不足");

        // ⚠ 核心断言：复用笔的库存在上一次就扣过了，**不能被回补**（回补就等于把上次的下单还回货架）
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);
        // 新建笔的二号店扣了又还
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK);
        // 仓库里只剩上一次那一笔，且它就是被复用的那一笔
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderRepository.all()).containsExactly(firstBatch.get(0));
        assertThat(stockPort.outboundRecords()).extracting(StockOutboundRecord::skuId)
                .containsExactly(SKU_A, SKU_B, SKU_B);
        assertThat(netOutbound()).isEqualTo(2);   // 净出库 = 复用那一笔的 2 件
    }

    @Test
    @DisplayName("复用 + 新建全部成功 → 复用笔不会被二次扣库存，也不会二次落库")
    void successfulMixedSubmissionKeepsReusedOrderUntouched() {
        OrderCreateCoordinator coordinator = coordinator(goodsQueryPort, stockPort);
        List<OrderModel> firstBatch = coordinator.create(command("req-1", line(SKU_A, 2)));

        List<OrderModel> secondBatch = coordinator.create(command("req-2", line(SKU_A, 2), line(SKU_B, 1)));

        assertThat(secondBatch.get(0)).isSameAs(firstBatch.get(0));
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK - 1);
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockPort.outboundRecords()).hasSize(2);
    }

    // ── ④ 回补自身的边界：幂等 + 次生错误不吞主异常 ────────────────────────────

    @Test
    @DisplayName("回补幂等：同 orderNo+skuId 回补两次只生效一次，且不再追加负记录")
    void revertIsIdempotent() {
        InMemoryStockPort stock = new InMemoryStockPort(clock);
        stock.setStock(SKU_A, 5);
        stock.deduct(SKU_A, 2, "NO-1");

        stock.revert(SKU_A, 2, "NO-1");
        stock.revert(SKU_A, 2, "NO-1");

        assertThat(stock.available(SKU_A)).isEqualTo(5);
        assertThat(stock.outboundRecords()).hasSize(2);   // 一正一负，重复回补不再追加
    }

    @Test
    @DisplayName("回补过程出错 → 主异常原样抛出，次生错误只挂 suppressed（不吞、也不换异常类型）")
    void secondaryFailureDoesNotMaskPrimary() {
        OrderCreateCoordinator coordinator = coordinator(
                new FailingGoodsQueryPort(goodsQueryPort, 3), new FailingRevertStockPort(stockPort));

        Throwable thrown = catchThrowable(() -> coordinator.create(command("req-1", line(SKU_A, 2))));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage()).isEqualTo("商品域暂不可用");
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("回补失败");
    }

    @Test
    @DisplayName("全部失败路径都不落单：连续两次失败的提交，仓库始终为空")
    void nothingIsSavedOnFailure() {
        // ⚠ 每次失败都换一个**新的**失败端口：失败按调用次数计，复用同一个包装会让第二次提交的
        // 调用序号早已越过失败点、从而「意外成功」——那样的用例看着在验失败，其实什么都没验到
        assertThatThrownBy(() -> coordinator(new FailingGoodsQueryPort(goodsQueryPort, 3), stockPort)
                .create(command("req-1", line(SKU_A, 1))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> coordinator(new FailingGoodsQueryPort(goodsQueryPort, 3), stockPort)
                .create(command("req-2", line(SKU_A, 1))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isZero();
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(netOutbound()).isZero();
        assertThat(orderRepository.findByRequestId("req-1")).isEmpty();
        assertThat(orderRepository.findByRequestId("req-2")).isEmpty();
    }

    /**
     * 第 N 次商品查询失败的端口（1=编排分组、2=goods-check、3=price-compute）
     *
     * <p>用调用次数而不是 mock 来注入失败，顺带把「每个步骤各自查一次商品」这条口径也钉住了：
     * 若哪天改成共用一次查询，这条用例的失败点就会挪位，测试会红。</p>
     */
    private static final class FailingGoodsQueryPort implements GoodsQueryPort {

        private final GoodsQueryPort delegate;
        private final int failAtCall;
        private final AtomicInteger calls = new AtomicInteger();

        private FailingGoodsQueryPort(GoodsQueryPort delegate, int failAtCall) {
            this.delegate = delegate;
            this.failAtCall = failAtCall;
        }

        @Override
        public Map<Long, SkuSnapshot> mapBySkuIds(Collection<Long> skuIds) {
            if (calls.incrementAndGet() == failAtCall) {
                throw new IllegalStateException("商品域暂不可用");
            }
            return delegate.mapBySkuIds(skuIds);
        }
    }

    /** 回补一律失败的库存端口：用来验证「次生错误不吞主异常」 */
    private static final class FailingRevertStockPort implements StockPort {

        private final StockPort delegate;

        private FailingRevertStockPort(StockPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean deduct(Long skuId, int quantity, String orderNo) {
            return delegate.deduct(skuId, quantity, orderNo);
        }

        @Override
        public void revert(Long skuId, int quantity, String orderNo) {
            throw new IllegalStateException("回补失败：库存服务不可用");
        }

        @Override
        public int available(Long skuId) {
            return delegate.available(skuId);
        }

        @Override
        public List<StockOutboundRecord> outboundRecords() {
            return delegate.outboundRecords();
        }
    }
}
