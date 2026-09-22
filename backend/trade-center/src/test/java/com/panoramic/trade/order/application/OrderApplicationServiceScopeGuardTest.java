package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderReceiveDTO;
import com.panoramic.contract.trade.dto.TradeOrderShipDTO;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.application.step.GoodsCheckStep;
import com.panoramic.trade.order.application.step.PriceComputeStep;
import com.panoramic.trade.order.application.step.StockCheckStep;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.infrastructure.DefaultOrderNoGenerator;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.support.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.support.InMemoryStockPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单**四个写**路径的锚点护栏：作用域为 {@code null} 一律 400（{@code ScopeGuard}）。
 *
 * <p>⚠ 守的是哪条默认路径：契约层 DTO 上的 {@code @NotNull} 只在 MVC 边界生效，绕过它的调用
 * （内部 Feign 直连、单测、将来的批处理）会把 {@code null} 一路送进仓储；而 {@code null} 在域内的
 * 语义是「**不限定**」（读侧要支持管理端全量视角），于是写路径会退化成「按单号取**任意一笔**并改它」
 * ——不报错、只是把别人的单改了。这类洞不会以任何形式自己暴露，只能钉死。</p>
 *
 * <p>⚠ 断言的是**入参不变量**（缺少参数 → 400），不是鉴权：提示语只说缺了什么，
 * 不出现「这单不是你的」之类的话术（那是域内鉴权，本域一律不做）。</p>
 *
 * <p>用真装配（内存端口 + 真步骤 + 真状态机）：护栏在方法第一句，真依赖一个都不会被碰到——
 * 而这正是要断言的（{@code verifyNoInteractions} 式的意思用「仓库里没有单、也没被写过」表达）。</p>
 */
class OrderApplicationServiceScopeGuardTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-09-21T04:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private InMemoryOrderRepository orderRepository;
    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(BASE_INSTANT, ZONE);
        InMemoryGoodsQueryPort goodsQueryPort = new InMemoryGoodsQueryPort();
        InMemoryStockPort stockPort = new InMemoryStockPort(clock);
        orderRepository = new InMemoryOrderRepository();

        OrderProperties properties = new OrderProperties();
        properties.setSteps(List.of(GoodsCheckStep.NAME, StockCheckStep.NAME, PriceComputeStep.NAME));
        properties.setStatusFlow(List.of(OrderStatus.values()));
        properties.setIdempotencyWindowSeconds(300);
        properties.setOrderNoMaxRetry(5);

        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(
                new GoodsCheckStep(goodsQueryPort), new StockCheckStep(stockPort), new PriceComputeStep(goodsQueryPort)),
                properties);
        AtomicInteger sequence = new AtomicInteger();
        OrderCreateCoordinator coordinator = new OrderCreateCoordinator(orderRepository, goodsQueryPort, stockPort,
                new DefaultOrderNoGenerator(clock, sequence::getAndIncrement), pipeline, properties, clock);

        service = new OrderApplicationService(coordinator, orderRepository, new OrderStatusFlow(List.of(OrderStatus.values())));
    }

    @Test
    @DisplayName("下单缺 customerId → 400，且不落任何单")
    void createWithoutScopeIsRejected() {
        TradeOrderCreateDTO dto = new TradeOrderCreateDTO();
        dto.setCustomerId(null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("支付缺 customerId → 400（不是「取任意一笔」），且订单不被改")
    void payWithoutScopeIsRejected() {
        TradeOrderPayDTO dto = new TradeOrderPayDTO();
        dto.setAmount(new BigDecimal("1.00"));

        assertThatThrownBy(() -> service.payOrder("202609221200000001", dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("发货缺 storeId → 400，且订单不被改")
    void shipWithoutScopeIsRejected() {
        TradeOrderShipDTO dto = new TradeOrderShipDTO();
        dto.setTrackingNo("SF1234567890");

        assertThatThrownBy(() -> service.shipOrder("202609221200000001", dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少店铺 id");

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("确认收货缺 customerId → 400，且订单不被改")
    void receiveWithoutScopeIsRejected() {
        TradeOrderReceiveDTO dto = new TradeOrderReceiveDTO();

        assertThatThrownBy(() -> service.receiveOrder("202609221200000001", dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");

        assertThat(orderRepository.count()).isZero();
    }
}
