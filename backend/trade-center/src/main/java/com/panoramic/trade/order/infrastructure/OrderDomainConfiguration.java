package com.panoramic.trade.order.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.trade.order.application.OrderCreateCoordinator;
import com.panoramic.trade.order.application.OrderCreatePipeline;
import com.panoramic.trade.order.application.OrderCreateStep;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderNoGenerator;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.jdbc.JdbcOrderRepository;
import com.panoramic.trade.order.infrastructure.service.TradeOrderItemService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderStatusLogService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderSubmissionOrderService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderSubmissionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 于是本类只做四件事：装配状态机、装配订单仓库、把步骤串成流水线、装配下单编排器。</p>
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
 * <h3>两个下游端口（商品 / 库存）不在本类</h3>
 * <p>它们由 store 域提供（{@code infrastructure/feign} 的 {@code StoreFeignAdapterConfiguration}
 * 经 {@code StoreClient} 调真实 store 域）——单独放一个配置类，是因为它有自己的开关
 * （{@code panoramic.trade.order.store-adapter}）与条件装配语义，与本类的「步骤链 + 仓库」是两件事；
 * 换实现在那边是一次整文件替换，不必回到本类里挑 bean 方法。步骤链与编排器只按端口类型注入，
 * 换实现不影响它们。</p>
 *
 * <h3>订单仓库：两种实现由配置二选一</h3>
 * <p>{@code panoramic.trade.order.repository} = {@code jdbc}（真实落库）或 {@code memory}
 * （无数据源场景：装配层单测、将来不需要库的切片测试）。⚠ 两边的 bean **方法名不同、类型也不同**
 * （{@code jdbcOrderRepository} / {@code inMemoryOrderRepository}），靠 {@code @ConditionalOnProperty}
 * 保证同一时刻只有一个 OrderRepository bean；消费方（编排器 / 应用服务）一律按端口类型注入。</p>
 * <p>⚠ 两个分支都**不设 {@code matchIfMissing}**：配置里没写这个键时，两边都不装配，
 * 报错是「找不到 OrderRepository 类型的 bean」——起不来，而不是静默挑一个实现。</p>
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
     * 订单仓库（真实落库：5 张表 + MyBatis-Plus 基类）
     *
     * <p>⚠ 五个 {@code *Service} 是容器里的 MP 服务 bean（本类不 {@code @ComponentScan} 到它们——
     * 生产由启动类扫，装配层单测走 memory 分支不需要它们）。</p>
     */
    @Bean
    @ConditionalOnProperty(name = "panoramic.trade.order.repository", havingValue = "jdbc")
    public JdbcOrderRepository jdbcOrderRepository(TradeOrderSubmissionService submissionService,
                                                   TradeOrderSubmissionOrderService submissionOrderService,
                                                   TradeOrderService orderService,
                                                   TradeOrderItemService itemService,
                                                   TradeOrderStatusLogService statusLogService,
                                                   OrderStatusFlow orderStatusFlow,
                                                   ObjectMapper objectMapper,
                                                   Clock clock) {
        return new JdbcOrderRepository(submissionService, submissionOrderService, orderService, itemService,
                statusLogService, orderStatusFlow, objectMapper, clock);
    }

    /**
     * 订单仓库（内存实现：无数据源的单测 / 切片场景）
     */
    @Bean
    @ConditionalOnProperty(name = "panoramic.trade.order.repository", havingValue = "memory")
    public InMemoryOrderRepository inMemoryOrderRepository() {
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
     * 下单编排器（拆单 / 两级幂等 / 失败整次回滚）
     *
     * <p>⚠ 本 bean 由 {@code @Bean} 方法产出，{@code BeanDefinition.getBeanClassName()} **恒为空**——
     * 故 Seata 的 {@code @GlobalTransactional} **不得挂在这个 bean 的方法上**：
     * {@code GlobalTransactionScanner} 按该值挑要增强的 bean，取不到类名即**静默跳过**（不报错、不开事务）。
     * 全局事务落在用例入口 {@code OrderApplicationService#create}，本地 {@code @Transactional} 仍在
     * 编排器方法上——两者分工见各自的类注释。</p>
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
