package com.panoramic.trade.order.application;

import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
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

/**
 * 两级幂等（裁定 D6）：① 请求级（`requestId`）命中即整批原样返回；② 拆单后每笔按指纹在**时间窗口**内判重。
 *
 * <p>⚠ 两级幂等是**两条独立的支路**，各自都要验「命中」与「未命中」：只测一条会漏掉另一条的回归——
 * 比如把「窗口外」也判成重复，后果是顾客过一会儿再买同一批东西永远下不了单（而这不会报任何错）。</p>
 *
 * <p>⚠ 「命中」不只是「返回了东西」，而是**没有任何副作用**：不再扣库存、不再落库、不再校验商品。
 * 故用例除了断言返回对象同一，还要断言库存与仓库条数都没变。</p>
 *
 * <p>⚠ 第一级返回的是「首次那次提交返回过的**整批**」，条数与订单号都要逐一致：
 * 一批里可能含指纹命中的复用笔（它们的 requestId 属于上一次提交），故凭证只能是那次提交的记录本身，
 * 不能是「按 requestId 查出来的订单行」。另有四条边界必须钉住：整批重放（含复用笔）、
 * 「整批全是复用笔」时**仍要写记录**、「同一 requestId 换内容复用」返回的仍是首批，
 * 以及「requestId 的作用域是顾客内」——最后一条错法的后果是把别人的订单交给当前顾客。</p>
 *
 * <p>⚠ 时间窗口的边界是**闭区间**（{@code createTime >= since}）：窗口长度那一刻算窗口内。
 * 边界必须有用例钉住，否则「临界 1 秒」的行为只能靠读代码猜。</p>
 */
class OrderIdempotencyTest {

    private static final Long STORE_A = 7L;
    private static final Long STORE_B = 8L;
    private static final Long SKU_A = 10L;
    private static final Long SKU_B = 20L;
    private static final int STOCK = 10;

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
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_B, STORE_B, "二号店", "10.00"));
        stockPort.setStock(SKU_A, STOCK);
        stockPort.setStock(SKU_B, STOCK);

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

    private OrderCreateCommand command(Long customerId, OrderSource source, String requestId,
                                       OrderCreateCommand.Line... lines) {
        return new OrderCreateCommand(customerId, source, ADDRESS, requestId, List.of(lines));
    }


    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    private static OrderCreateCommand.Line line(Long skuId, int quantity) {
        return new OrderCreateCommand.Line(skuId, quantity);
    }

    // ── 第一级：请求级幂等 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("同 requestId 重放 → 返回同一批对象、库存不二次扣、仓库条数不变")
    void sameRequestIdReturnsTheSameBatch() {
        List<OrderModel> first = coordinator.create(command(11L, OrderSource.DIRECT, "req-1", line(SKU_A, 2)));
        int stockAfterFirst = stockPort.available(SKU_A);

        List<OrderModel> replay = coordinator.create(command(11L, OrderSource.DIRECT, "req-1", line(SKU_A, 2)));

        assertThat(replay).hasSize(1);
        assertThat(replay.get(0)).isSameAs(first.get(0));
        assertThat(stockPort.available(SKU_A)).isEqualTo(stockAfterFirst);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockPort.outboundRecords()).hasSize(1);
    }

    @Test
    @DisplayName("requestId 命中时连商品都不再校验（重放不该比第一次更早失败）")
    void requestIdHitSkipsGoodsValidation() {
        List<OrderModel> first = coordinator.create(command(11L, OrderSource.DIRECT, "req-1", line(SKU_A, 2)));
        // 商品此刻「查不到了」：重放仍应原样返回，而不是 400「商品不存在」
        goodsQueryPort.clear();

        List<OrderModel> replay = coordinator.create(command(11L, OrderSource.DIRECT, "req-1", line(SKU_A, 2)));

        assertThat(replay).hasSize(1);
        assertThat(replay.get(0)).isSameAs(first.get(0));
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("「一店复用 + 一店新建」的提交被重放 → 返回与首次**同一批**（条数与订单号逐一致）")
    void replayedMixedBatchReturnsTheWholeFirstBatch() {
        // 首次只买 A 店，把 A 店那一笔落下来
        OrderModel orderA = coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_A, 2))).get(0);

        // 再提交 req-2：A 店指纹命中复用 orderA、B 店新建 orderB
        List<OrderModel> first = coordinator.create(
                command(11L, OrderSource.CART, "req-2", line(SKU_A, 2), line(SKU_B, 1)));
        assertThat(first).hasSize(2);
        assertThat(first.get(0)).isSameAs(orderA);   // 复用笔的 requestId 仍是 req-1

        // 客户端超时，原样重放 req-2
        List<OrderModel> replay = coordinator.create(
                command(11L, OrderSource.CART, "req-2", line(SKU_A, 2), line(SKU_B, 1)));

        // ⚠ 若按「requestId 查订单行」实现第一级，这里只会返回 req-2 新建的那一笔——同一请求两次调用条数不同
        assertThat(replay).hasSize(2);
        assertThat(replay).containsExactlyElementsOf(first);
        assertThat(replay).extracting(OrderModel::getOrderNo)
                .containsExactlyElementsOf(first.stream().map(OrderModel::getOrderNo).toList());
        assertThat(replay.get(0)).isSameAs(orderA);
        // 重放没有任何副作用：不重建、不二次扣库存、不重复落库
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK - 1);
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockPort.outboundRecords()).hasSize(2);
    }

    @Test
    @DisplayName("requestId 的作用域是顾客内：B 顾客拿 A 用过的键 → 不返回 A 的订单，按新提交正常处理")
    void requestIdIsScopedToTheCustomer() {
        List<OrderModel> aBatch = coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_A, 2)));

        List<OrderModel> bBatch = coordinator.create(command(99L, OrderSource.CART, "req-1", line(SKU_A, 2)));

        // ⚠ 只按 requestId 查的话，这里会把 11 号顾客的订单原样交给 99 号（订单号 / 金额 / 门店外泄）
        assertThat(bBatch).singleElement().satisfies(order -> {
            assertThat(order.getCustomerId()).isEqualTo(99L);
            assertThat(order.getOrderNo()).isNotEqualTo(aBatch.get(0).getOrderNo());
        });
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 4);
        // A 的提交记录没被 B 顶掉，A 重放拿到的仍是自己那一笔
        assertThat(orderRepository.findCommittedBatch(11L, "req-1").orElseThrow()).containsExactlyElementsOf(aBatch);
        assertThat(orderRepository.findCommittedBatch(99L, "req-1").orElseThrow()).containsExactlyElementsOf(bBatch);
    }

    @Test
    @DisplayName("requestId 为空 / 空白串 → 第一级不生效，但第二级仍生效（两级互不替代）")
    void blankRequestIdFallsBackToFingerprintLevel() {
        List<OrderModel> first = coordinator.create(command(11L, OrderSource.DIRECT, null, line(SKU_A, 2)));
        List<OrderModel> second = coordinator.create(command(11L, OrderSource.DIRECT, "   ", line(SKU_A, 2)));

        // 第一级没生效（requestId 为空）、第二级生效（指纹 + 窗口命中）→ 复用首单而不是新建
        assertThat(second.get(0)).isSameAs(first.get(0));
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockPort.outboundRecords()).hasSize(1);
    }

    @Test
    @DisplayName("整批全是复用笔（本次一笔都没新建）→ 记录仍要写下，重放原样返回整批")
    void allReusedBatchStillRecordsTheSubmission() {
        List<OrderModel> first = coordinator.create(
                command(11L, OrderSource.CART, "req-1", line(SKU_A, 2), line(SKU_B, 1)));
        assertThat(first).hasSize(2);

        // req-2 内容与 req-1 逐字相同 → 两笔都指纹命中，本次 created 为空
        List<OrderModel> allReused = coordinator.create(
                command(11L, OrderSource.CART, "req-2", line(SKU_A, 2), line(SKU_B, 1)));
        assertThat(allReused).containsExactlyElementsOf(first);

        // ⚠ 记录里必须有 req-2 这条：若把它「优化」成「created 为空就不写」，重放会查不到记录、返回空批
        assertThat(orderRepository.findCommittedBatch(11L, "req-2").orElseThrow()).containsExactlyElementsOf(first);
        assertThat(coordinator.create(command(11L, OrderSource.CART, "req-2", line(SKU_A, 2), line(SKU_B, 1))))
                .containsExactlyElementsOf(first);
        // 复用不落库、不扣库存：全程只有首批那两笔与那一次扣减
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK - 1);
        assertThat(stockPort.outboundRecords()).hasSize(2);
    }

    @Test
    @DisplayName("同一 requestId 被换内容复用（客户端 bug）→ 仍返回首次那批，新内容不下单（已登记的口径）")
    void sameRequestIdWithDifferentContentReturnsTheFirstBatch() {
        List<OrderModel> first = coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_A, 2)));

        // 同一个键、换了商品：一级幂等先命中，故返回首批，B 店那笔根本不会被建出来
        List<OrderModel> second = coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_B, 1)));

        assertThat(second).containsExactlyElementsOf(first);
        assertThat(second.get(0).getOrderNo()).isEqualTo(first.get(0).getOrderNo());
        // 副作用为零：B 店既没下单也没扣库存，凭证也没被新内容顶掉
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK);
        assertThat(orderRepository.findCommittedBatch(11L, "req-1").orElseThrow()).containsExactlyElementsOf(first);
    }

    // ── 第二级：指纹 + 时间窗口 ─────────────────────────────────────────────────

    @Test
    @DisplayName("不同 requestId 但同指纹且在窗口内 → 复用首单（原样返回，不重建、不再扣库存）")
    void sameFingerprintWithinWindowReusesFirstOrder() {
        List<OrderModel> first = coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_A, 2), line(SKU_B, 3)));
        clock.advanceSeconds(299);

        List<OrderModel> second = coordinator.create(command(11L, OrderSource.CART, "req-2", line(SKU_B, 3), line(SKU_A, 2)));

        assertThat(second).hasSize(2);
        // 行顺序不同不影响指纹（OrderFingerprint 对行顺序不敏感），故两笔都复用了
        assertThat(second.get(0)).isSameAs(first.get(0));
        assertThat(second.get(1)).isSameAs(first.get(1));
        // 复用意味着「原样返回」：连 requestId 都还是上一批的
        assertThat(second.get(0).getRequestId()).isEqualTo("req-1");
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK - 3);
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("窗口边界是闭区间：+300 秒仍复用，+301 秒即新单")
    void windowBoundaryIsInclusive() {
        List<OrderModel> first = coordinator.create(command(11L, OrderSource.DIRECT, "req-1", line(SKU_A, 2)));

        clock.advanceSeconds(300);
        List<OrderModel> onBoundary = coordinator.create(command(11L, OrderSource.DIRECT, "req-2", line(SKU_A, 2)));
        assertThat(onBoundary.get(0)).isSameAs(first.get(0));

        clock.advanceSeconds(1);
        List<OrderModel> outOfWindow = coordinator.create(command(11L, OrderSource.DIRECT, "req-3", line(SKU_A, 2)));
        assertThat(outOfWindow.get(0)).isNotSameAs(first.get(0));
        assertThat(outOfWindow.get(0).getOrderNo()).isNotEqualTo(first.get(0).getOrderNo());
        assertThat(outOfWindow.get(0).getCreateTime()).isAfter(first.get(0).getCreateTime());
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 4);
    }

    @Test
    @DisplayName("指纹不同 → 新单：数量 / 来源 / 顾客任一不同都不是重复提交")
    void differentFingerprintCreatesNewOrder() {
        coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_A, 2)));
        // 数量不同（买 1 件与买 2 件是两笔单）
        coordinator.create(command(11L, OrderSource.CART, "req-2", line(SKU_A, 3)));
        // 来源不同（详情页直购与购物车结算是两次意图不同的提交）
        coordinator.create(command(11L, OrderSource.DIRECT, "req-3", line(SKU_A, 2)));
        // 顾客不同（不同顾客的同一批商品不该互相顶掉）
        coordinator.create(command(99L, OrderSource.CART, "req-4", line(SKU_A, 2)));

        assertThat(orderRepository.count()).isEqualTo(4);
        assertThat(orderRepository.all()).extracting(OrderModel::getOrderNo).doesNotHaveDuplicates();
        assertThat(orderRepository.all()).extracting(OrderModel::getFingerprint).doesNotHaveDuplicates();
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2 - 3 - 2 - 2);
    }

    @Test
    @DisplayName("同一次提交里「一店复用 + 一店新建」：返回整批且仍按 storeId 升序，复用笔不重复落库")
    void mixedReuseAndCreateReturnsWholeBatch() {
        // 先只买 A 店的商品，把 A 店那一笔落下来
        List<OrderModel> firstBatch = coordinator.create(command(11L, OrderSource.CART, "req-1", line(SKU_A, 2)));
        OrderModel reusedOrder = firstBatch.get(0);

        // 再提交 A + B：A 店指纹命中复用，B 店新建
        List<OrderModel> secondBatch = coordinator.create(
                command(11L, OrderSource.CART, "req-2", line(SKU_A, 2), line(SKU_B, 1)));

        assertThat(secondBatch).hasSize(2);
        assertThat(secondBatch.get(0)).isSameAs(reusedOrder);
        assertThat(secondBatch.get(1).getStoreId()).isEqualTo(STORE_B);
        assertThat(secondBatch).extracting(OrderModel::getStoreId).containsExactly(STORE_A, STORE_B);

        // 复用笔不重复落库：仓库里是「A 的首单 + B 的新单」两笔
        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(orderRepository.all()).contains(reusedOrder);
        // A 店只被扣过一次（复用不扣）、B 店扣了 1
        assertThat(stockPort.available(SKU_A)).isEqualTo(STOCK - 2);
        assertThat(stockPort.available(SKU_B)).isEqualTo(STOCK - 1);
    }
}
