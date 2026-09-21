package com.panoramic.trade.order.infrastructure;

import com.panoramic.trade.order.application.OrderCreateCoordinator;
import com.panoramic.trade.order.application.OrderCreatePipeline;
import com.panoramic.trade.order.application.OrderCreateStep;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderNoGenerator;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

/**
 * 订单领域的装配层：把「配置」与「可插拔步骤」接成一条可运行的链（裁定 D9/D10）。
 *
 * <p>⚠ <b>为什么装配集中在一个配置类而不是每个类各自 {@code @Component}</b>：
 * 步骤实现是「可插拔」的（新增自定义步骤 = 新增一个 {@code @Component} + yml 里加个名字），
 * 而「串成链」与「选哪个适配器」是装配决策——两者混在一起后，换一个端口实现就得去动业务类。
 * 于是本类只做三件事：装配状态机、装配端口的内存实现、把步骤串成流水线。</p>
 *
 * <p>⚠ <b>{@code @ComponentScan} 只扫步骤包</b>：装配层单测用 {@code @SpringJUnitConfig} 直接加载本类，
 * 不会经过启动类的 {@code scanBasePackages = "com.panoramic"}，故这里必须自己把可插拔步骤收进来，
 * 否则容器里一个步骤都没有、流水线装配即失败。扫描范围刻意收窄到步骤包——本类只依赖它们，
 * 扫一大片会把「本类到底装配了什么」变模糊。生产启动时启动类也会扫到这批 {@code @Component}，
 * 与这里的扫描**按 bean 名去重**（同一个类、同一个 bean 名），不会重复注册。</p>
 *
 * <p>⚠ <b>{@code Clock} 是一个 bean，不是 {@code LocalDateTime.now()}</b>：订单的创建时刻与幂等窗口
 * 都从它取，单测注入固定/可推进的时钟才能钉住「窗口内复用、窗口外新单」这条边界。
 * 生产用系统时钟，语义与原来的 {@code now()} 完全一致。</p>
 *
 * <p>⚠ <b>三个端口 bean 返回的是内存实现的具体类型</b>（而非端口接口）：本期只有内存实现（裁定 D1），
 * 而装配层单测要用它们的预置入口（{@code put} / {@code setStock}）。将来接真实适配器时，
 * 把这里的 {@code @Bean} 方法换成真实实现即可，**domain / application 一行都不用动**（残留清单 3）。</p>
 */
@Configuration
@EnableConfigurationProperties(OrderProperties.class)
@ComponentScan(basePackages = "com.panoramic.trade.order.application.step")
public class OrderDomainConfiguration {

    /**
     * 订单领域统一时钟
     *
     * @return 系统默认时区的时钟（单测覆盖为固定/可推进的时钟）
     */
    @Bean
    public Clock orderClock() {
        return Clock.systemDefaultZone();
    }

    /**
     * 订单状态机（顺序来自配置；构造期即断言配置覆盖 {@code OrderStatus} 全部常量，缺一个就起不来）
     *
     * @param properties 订单配置
     * @return 由 {@code panoramic.trade.order.status-flow} 建起的状态机
     */
    @Bean
    public OrderStatusFlow orderStatusFlow(OrderProperties properties) {
        return new OrderStatusFlow(properties.getStatusFlow());
    }

    /**
     * 商品查询端口（本期为内存实现）
     */
    @Bean
    public InMemoryGoodsQueryPort goodsQueryPort() {
        return new InMemoryGoodsQueryPort();
    }

    /**
     * 库存端口（本期为内存实现；时钟注入是为了让出库记录的发生时刻可预测）
     */
    @Bean
    public InMemoryStockPort stockPort(Clock clock) {
        return new InMemoryStockPort(clock);
    }

    /**
     * 订单仓库（本期为内存实现）
     */
    @Bean
    public InMemoryOrderRepository orderRepository() {
        return new InMemoryOrderRepository();
    }

    /**
     * 订单号生成器（格式与长度见 {@link DefaultOrderNoGenerator}）
     */
    @Bean
    public OrderNoGenerator orderNoGenerator(Clock clock) {
        return new DefaultOrderNoGenerator(clock);
    }

    /**
     * 订单生成流水线：把容器里的步骤 bean 按配置顺序串起来
     *
     * <p>⚠ 入参是容器里**全部** {@code OrderCreateStep}，谁跑、按什么顺序完全由配置决定——
     * 步骤 bean 的发现顺序不参与决策。</p>
     *
     * @param availableSteps 容器里全部步骤 bean
     * @param properties     订单配置（{@code panoramic.trade.order.steps}）
     * @return 装配好的流水线
     * @throws IllegalStateException 配置为空、步骤名重复、配置里出现容器中没有的步骤名（装配期即失败）
     */
    @Bean
    public OrderCreatePipeline orderCreatePipeline(List<OrderCreateStep> availableSteps, OrderProperties properties) {
        return new OrderCreatePipeline(availableSteps, properties);
    }

    /**
     * 下单编排器（拆单 / 两级幂等 / 失败整次回滚；Seata 的落点见其类注释）
     *
     * @return 编排器
     */
    @Bean
    public OrderCreateCoordinator orderCreateCoordinator(OrderRepository orderRepository,
                                                         GoodsQueryPort goodsQueryPort,
                                                         StockPort stockPort,
                                                         OrderNoGenerator orderNoGenerator,
                                                         OrderCreatePipeline orderCreatePipeline,
                                                         OrderProperties properties,
                                                         Clock clock) {
        return new OrderCreateCoordinator(orderRepository, goodsQueryPort, stockPort,
                orderNoGenerator, orderCreatePipeline, properties, clock);
    }
}
