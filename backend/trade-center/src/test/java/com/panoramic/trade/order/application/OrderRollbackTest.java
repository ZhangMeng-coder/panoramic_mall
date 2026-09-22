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
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.support.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.support.InMemoryStockPort;
import com.panoramic.trade.order.support.StockOutboundRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
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

    private OrderCreateCoordinator coordinator(GoodsQueryPort goods, StockPort stock) {
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goods), new StockCheckStep(stock), new PriceComputeStep(goods)), properties);
        // 单号序列每个协调器各自从 0 起：失败提交不落库，故两个协调器吐出同一个单号是**允许**的、
        // 也是真实存在的情形（见第 ⑤ 组用例）——不能靠「让它们错开」来回避
        AtomicInteger sequence = new AtomicInteger();
        return new OrderCreateCoordinator(orderRepository, goods, stock,
                new DefaultOrderNoGenerator(clock, sequence::getAndIncrement), pipeline, properties, clock);
    }

    /** 收货地址与各用例的断言无关，取一份合法值即可（地址校验在 {@code OrderAddress} 自己那侧） */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private static OrderCreateCommand command(String requestId, OrderCreateCommand.Line... lines) {
        return new OrderCreateCommand(11L, OrderSource.CART, ADDRESS, requestId, List.of(lines));
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
        // 幂等映射也没落：否则「这次提交已经成功过」会被记下来，重放会返回一个根本不存在的批次
        assertThat(orderRepository.findCommittedBatch(11L, "req-1")).isEmpty();
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
    @DisplayName("按单回补：一笔订单只调一次 revertByOrder，其中没扣成的行不产生反向流水")
    void linesWithZeroNetDeductionAreNotReverted() {
        // ⚠ 「该还谁、还多少」不再由编排层按行算净额（见 StockPort#revertByOrder 契约）：编排层只交出
        // 订单号，净额算式在实现侧。故这里分开验两件事——调用**粒度**是「单」而不是「行」；
        // 而「没扣过的行不会被还」是**可观察结果**（没有它的反向流水、库存没被冲成正数）。
        // 二号店两行里 SKU_B 扣成、SKU_B2 库存为 0 压根没扣，只有前者该被还。
        CountingStockPort counting = new CountingStockPort(stockPort);

        Throwable thrown = catchThrowable(() -> coordinator(goodsQueryPort, counting)
                .create(command("req-1", line(SKU_B, 1), line(SKU_B2, 1))));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).isEqualTo("库存不足");
        assertThat(counting.revertedOrderNos()).hasSize(1);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK);
        assertThat(stockPort.available(SKU_B2)).isZero();   // 没扣过 → 没还，也就不会把库存冲成 1
        assertThat(stockPort.outboundRecords()).extracting(StockOutboundRecord::skuId)
                .containsExactly(SKU_B, SKU_B);             // 只有扣成过的那一行有「正 + 负」两条
    }

    // ── ⑤ 跨 episode 撞同一单号：回补必须真的发生（同一单号被复用也不许被当成「还过了」） ──

    @Test
    @DisplayName("两次失败提交拿到同一单号 + 含同一 skuId → 两次都真的回补（库存回原值、流水净额 0）")
    void twoFailedEpisodesSharingTheSameOrderNoBothRevert() {
        // 失败提交从不落库 → existsByOrderNo 对它恒 false → 同一秒里的两次失败提交**完全可能**拿到同一单号
        // （生产上是随机序列 1/10000，而重试/补偿场景下就是同一次意图被重放）。这里把序列固定成 0，
        // 让这条路径成为必然，而不是靠碰运气才走到。
        assertThatThrownBy(() -> fixedOrderNoCoordinator(new FailingGoodsQueryPort(goodsQueryPort, 3))
                .create(command("req-1", line(SKU_A, 2))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fixedOrderNoCoordinator(new FailingGoodsQueryPort(goodsQueryPort, 3))
                .create(command("req-2", line(SKU_A, 2))))
                .isInstanceOf(IllegalStateException.class);

        // ⚠ 若回补按 orderNo+skuId 去重（「这个键我调过」），第二次回补会被当成重复调用静默吃掉：
        // 库存永久少 2 件，而出库流水净额却显示 0——账实不符，且不报任何错。
        // 现行口径按**流水净额**判（见 StockPort#revertByOrder）：第一次已把净额还成 0，
        // 第二次重新扣了 +2，净额又是 +2 → 照样该还。判据是记账事实，不是调用痕迹。
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(netOutbound()).isZero();
        assertThat(stockPort.outboundRecords()).hasSize(4);   // 两次「+2 扣 / −2 还」
    }

    /** 单号固定为同一个的协调器（见上一条用例：把「跨 episode 撞单号」变成必然） */
    private OrderCreateCoordinator fixedOrderNoCoordinator(GoodsQueryPort goods) {
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goods), new StockCheckStep(stockPort), new PriceComputeStep(goods)), properties);
        return new OrderCreateCoordinator(orderRepository, goods, stockPort,
                new DefaultOrderNoGenerator(clock, () -> 0), pipeline, properties, clock);
    }

    @Test
    @DisplayName("revertByOrder 幂等：同一单回补两次，第二次是 no-op（不会重复加库存、不追加流水）")
    void revertingTheSameOrderTwiceIsANoOp() {
        // ⚠ 幂等由**实现侧**按流水净额保证（StockPort#revertByOrder 契约），故这条断言直接打在实现上：
        // 编排层的失败回滚会被重复触发（同一失败 episode 重放、将来 Seata 补偿重试），
        // 若每次调用都无条件加库存，那是一次就翻倍的超卖。
        assertThat(stockPort.deduct(SKU_A, 2, "202609211200000001")).isTrue();
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);

        stockPort.revertByOrder("202609211200000001");
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);

        stockPort.revertByOrder("202609211200000001");   // 第二次

        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
        assertThat(stockPort.outboundRecords()).hasSize(2);   // 只有「+2 扣 / −2 还」两条，没有多余的负流水
        // 没扣过的单号也照样 no-op（补偿路径宁可不做也不能炸，R19）
        stockPort.revertByOrder("202609211200000999");
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK);
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
        assertThat(orderRepository.findCommittedBatch(11L, "req-1")).isEmpty();
        assertThat(orderRepository.findCommittedBatch(11L, "req-2")).isEmpty();
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

    /** 记录「回补被调用了几次、分别针对哪一单」的库存端口包装：调用**粒度**（单 vs 行）必须看得见 */
    private static final class CountingStockPort implements StockPort {

        private final StockPort delegate;
        private final List<String> revertedOrderNos = new ArrayList<>();

        private CountingStockPort(StockPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean deduct(Long skuId, int quantity, String orderNo) {
            return delegate.deduct(skuId, quantity, orderNo);
        }

        @Override
        public void revertByOrder(String orderNo) {
            revertedOrderNos.add(orderNo);
            delegate.revertByOrder(orderNo);
        }

        @Override
        public int available(Long skuId) {
            return delegate.available(skuId);
        }

        private List<String> revertedOrderNos() {
            return List.copyOf(revertedOrderNos);
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
        public void revertByOrder(String orderNo) {
            throw new IllegalStateException("回补失败：库存服务不可用");
        }

        @Override
        public int available(Long skuId) {
            return delegate.available(skuId);
        }
    }
}
