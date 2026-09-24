package com.panoramic.trade.order.infrastructure;

import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.SpecAttr;
import com.panoramic.contract.store.dto.StoreGoodsSkuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreStockDeductDTO;
import com.panoramic.contract.store.vo.StoreGoodsSkuSnapshotVO;
import com.panoramic.trade.order.application.OrderCreateCommand;
import com.panoramic.trade.order.application.OrderCreateCoordinator;
import com.panoramic.trade.order.application.OrderCreatePipeline;
import com.panoramic.trade.order.application.OrderCreateStep;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.infrastructure.feign.GoodsQueryAdapter;
import com.panoramic.trade.order.infrastructure.feign.StockAdapter;
import com.panoramic.trade.order.infrastructure.feign.StoreFeignAdapterConfiguration;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 装配层：**配置与代码的漂移守卫**，以及「装配出来的东西真能跑通一单」。
 *
 * <p>⚠ 本类用 {@code @SpringJUnitConfig} 而不是 {@code @SpringBootTest}（裁定 D10）：
 * trade-center 的 {@code spring.config.import: nacos:datasource-mysql.yml} **不带 {@code optional:}**，
 * 没有 Nacos 时 Spring Boot 上下文启动即失败——{@code @SpringBootTest} 不是「更完整」，是根本跑不起来。
 * 故这里只加载 {@link OrderDomainConfiguration}（它自己 {@code @ComponentScan} 步骤包，
 * 不依赖启动类的扫包路径），Nacos 的 import 根本不会被处理。</p>
 *
 * <p>⚠ <b>为什么必须读真实的 {@code src/main/resources/application.yml}</b>：这是本类存在的理由。
 * 步骤名、状态顺序、窗口、重试上限分散在「yml 文本」与「Java 代码」两处，任何一侧改了而另一侧没跟，
 * 编辑器不会报错、编译也不会报错（配置引用了不存在的步骤名只有**启动时**才炸）。
 * 于是让测试直接把那份真实文件解析出来、与容器里装配出的东西逐项对账——改了 yml 忘了改代码
 * （或反过来）会在这里红，而不是等到某次部署启动失败。</p>
 *
 * <p>⚠ 为了断言「装配真的读了这份配置」，本类把真实 yml 的 {@code panoramic.trade.order} 也喂进了
 * 测试上下文的环境（{@link RealApplicationYmlInitializer}）：否则 {@code OrderProperties} 全是默认值，
 * 「顺序 === 配置顺序」这类断言会退化成自说自话。</p>
 *
 * <p>⚠ <b>两个开关在测试上下文里怎么处理</b>：真实 yml 写的是 {@code repository: jdbc}，
 * 但本上下文**没有数据源**（也不该有：它只验域内装配，连上库就跑偏了），
 * 故用 {@link TestPropertySource} 把它盖成 {@code memory}（优先级高于 initialize 里 {@code addLast} 的真实 yml）。
 * 盖掉不等于不看——{@link #repositorySwitchIsOneOfTheSupportedValues()} 仍然把真实 yml 的那个值
 * 与「代码里认识的取值集合」对账，改名 / 打错字照样在这里红。
 * {@code store-adapter} 保持真实值 {@code feign}，故本类要一并加载 {@link StoreFeignAdapterConfiguration}，
 * 并为它提供 {@link StoreClient} 的**测试替身**（见 {@link StoreClientTestDoubleConfiguration}）——
 * 商品 / 库存端口本来就由那份装配提供，这也顺带验了「开关真的指到了真实 store 域那份装配」。</p>
 *
 * <p>⚠ <b>这里没有、也不该有「内存商品表」</b>：真适配器把每次查询都转成一次 store 域调用，
 * 故预置数据的入口是**打桩 {@code StoreClient}**，而不是往某个内存 map 里 put。
 * 这条差别本身就是「下游真的换了」的证据：从前换掉内存实现只需要换一个 map，
 * 现在不提供 {@code StoreClient} 就什么都装配不出来。</p>
 */
@SpringJUnitConfig(classes = {OrderDomainConfiguration.class, StoreFeignAdapterConfiguration.class,
        OrderDomainWiringTest.StoreClientTestDoubleConfiguration.class},
        initializers = OrderDomainWiringTest.RealApplicationYmlInitializer.class)
@TestPropertySource(properties = "panoramic.trade.order.repository=memory")
class OrderDomainWiringTest {

    /** 相对模块根目录（Maven surefire 的默认工作目录就是模块根，即 {@code backend/trade-center}） */
    private static final Path APPLICATION_YML = Path.of("src", "main", "resources", "application.yml");

    /**
     * Maven 资源过滤占位符（{@code @nacos.username@} 之类）
     *
     * <p>⚠ 源码里的 {@code application.yml} 在被 Maven 过滤**之前不是合法 YAML**——
     * {@code @} 不能作为标量的起始字符，snakeyaml 会在那一行直接报 scanner 错误。
     * 而过滤发生在 {@code process-resources}，产出的 {@code target/classes/application.yml} 才是运行时真正加载的版本。
     * 本用例刻意读**源码文件**（开发者改的就是它），故解析前先把占位符换成一个普通字符串：
     * 它们全部在 Nacos 段，与 {@code panoramic.trade.order} 无关；万一哪天有关，
     * 下面按类型取值（{@code Number}）也会立刻炸出来，不会静默读到一个假值。</p>
     */
    private static final Pattern MAVEN_PLACEHOLDER = Pattern.compile("@[A-Za-z0-9_.\\-]+@");

    /** 商品 / 库存指向谁（{@code mock} 侧那份装配已随 T4b 删除，本键只剩这一个取值） */
    private static final String STORE_ADAPTER_KEY = "panoramic.trade.order.store-adapter";

    private static final String FEIGN = "feign";

    private static final Long SKU_ID = 910L;
    private static final Long SPU_ID = 1910L;
    private static final Long STORE_ID = 7L;

    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    /**
     * store 域的**测试替身**：本类只验装配，故只提供 {@link StoreClient} 这一个 bean，
     * 具体行为（快照长什么样、库存够不够）由用例自己打桩（见 {@link OrderDomainWiringTest#givenSellableSkuWithStock}）。
     *
     * <p>⚠ 用 Mockito 而不是手写空实现：{@code StoreClient} 有 27 个方法，逐条 no-op 覆盖不值得
     * ——本类要的只是「有一个能装得进去的 StoreClient」。</p>
     */
    @Configuration
    static class StoreClientTestDoubleConfiguration {

        @Bean
        StoreClient storeClient() {
            return Mockito.mock(StoreClient.class);
        }
    }

    @Autowired
    private OrderProperties properties;

    @Autowired
    private OrderStatusFlow statusFlow;

    @Autowired
    private OrderCreatePipeline pipeline;

    @Autowired
    private OrderCreateCoordinator coordinator;

    @Autowired
    private List<OrderCreateStep> stepBeans;

    @Autowired
    private StoreClient storeClient;

    @Autowired
    private GoodsQueryPort goodsQueryPort;

    @Autowired
    private StockPort stockPort;

    @Autowired
    private InMemoryOrderRepository orderRepository;

    @BeforeEach
    void resetInMemoryState() {
        // 订单仓库是单例，用例之间会互相残留；每例从空仓库开始，并把下游替身的打桩与调用记录一起清掉
        orderRepository.clear();
        Mockito.reset(storeClient);
    }

    // ── 漂移守卫：真实 yml 与装配出的东西逐项对账 ────────────────────────────────

    @Test
    @DisplayName("配置被真实读入：steps / status-flow / 窗口 / 重试上限都与 application.yml 一致")
    void realConfigIsBound() {
        assertThat(properties.getSteps()).containsExactlyElementsOf(configuredSteps());
        // ⚠ 本格比的是**配置自己**（绑进来的值 ↔ yml 原文）：主链是**有序**列表，故连顺序一起比
        //    （顺序即先后，是本版唯一承载顺序的地方）；「机器读出来的主链 == 配置」在
        //    statusFlowMatchesConfiguredChain 里比
        assertThat(properties.getStatusFlow()).containsExactlyElementsOf(configuredStatusChain());
        assertThat(properties.getIdempotencyWindowSeconds())
                .isEqualTo(configuredWindowSeconds());
        assertThat(properties.getOrderNoMaxRetry()).isEqualTo(configuredMaxRetry());
    }

    @Test
    @DisplayName("① 配置里的每个步骤名都能在步骤 bean 的 name() 集合里找到")
    void everyConfiguredStepHasABean() {
        Set<String> beanNames = stepBeans.stream().map(OrderCreateStep::name).collect(Collectors.toSet());

        // 只查这一个方向：容器里有未被配置启用的步骤 bean 是**合法**的（步骤可插拔，跑哪些由配置说了算）；
        // 反过来「配置写了却不存在的步骤」才是错误，且它此刻已经会让上下文起不来
        assertThat(beanNames).containsAll(configuredSteps());
    }

    @Test
    @DisplayName("② 装配出的状态机与 yml 那份主链逐项一致（顺序、入口、每一步的下一步都来自配置）")
    void statusFlowMatchesConfiguredChain() {
        List<OrderStatus> chain = configuredStatusChain();

        // ⚠ 这是**绑定的漂移守卫**：配置侧走「SnakeYAML 直读原文」，状态机侧走
        //    「Boot 绑定 → OrderProperties → OrderStatusFlow」——两条不同的数据路径。
        //    枚举名绑定失败、列表整个绑没了、yml 改成 kebab-case 的键，都会在这里红。
        assertThat(statusFlow.chain()).containsExactlyElementsOf(chain);
        // 入口 = 主链首项（它同时是轨迹的首项与 OrderModel#open 出来的状态）
        assertThat(statusFlow.initialState()).isEqualTo(chain.get(0));

        // 每一步的下一步都由主链给出：主链内相邻两项之间可推进，末项与两个结束过程的落点没有下一步
        for (OrderStatus status : EnumSet.allOf(OrderStatus.class)) {
            int index = chain.indexOf(status);
            OrderStatus expectedNext = (index < 0 || index == chain.size() - 1) ? null : chain.get(index + 1);
            assertThat(statusFlow.nextOf(status)).as("%s 的下一个状态", status).isEqualTo(expectedNext);
        }
    }

    @Test
    @DisplayName("③ 主链只由 yml 给出：两个结束过程的落点不许出现在配置里，来源状态在动作里")
    void statusChainIsMainLineOnly() {
        List<OrderStatus> chain = configuredStatusChain();

        // ① yml 里不许出现两个结束过程的落点——写进来 OrderStatusFlow 装配期就炸，且它比「少写一个键」
        //    更隐蔽：哪怕配的来源状态与动作里声明的一模一样也不允许（两处都写就成了第二份定义，
        //    不一致时以谁为准都不对）。这里把这条口径钉在**配置文本**上，而不只是钉在行为上。
        assertThat(chain).doesNotContainAnyElementsOf(ENDING_TARGETS);
        assertThat(chain).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(OrderStatus.class).stream()
                .filter((status) -> !ENDING_TARGETS.contains(status))
                .toList());
        assertThat(chain.get(0)).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // ⚠ 这里**不再**断言那两个结束过程的来源状态（旧版用 predecessorsOf 断言过）：状态机不知道也不该
        //    知道它们（它只认得「这个落点不在主链上」），来源写在 {@code OrderModel#markCancelled} /
        //    {@code #markRefunded} 里——验它的是 OrderModelTest，不是本类这条「配置 ↔ 代码」的对账。
    }

    @Test
    @DisplayName("④ 装配出的流水线执行顺序 === 配置里 steps 的顺序")
    void pipelineOrderMatchesConfiguration() {
        assertThat(pipeline.stepNames()).containsExactlyElementsOf(configuredSteps());
    }

    @Test
    @DisplayName("两个开关的取值都在代码认识的集合里（store-adapter 只剩 feign；repository 不设默认值 → 写错即起不来）")
    void repositorySwitchIsOneOfTheSupportedValues() {
        // ⚠ 这两个键刻意没有默认值：缺失或写错时对应 bean 一个都不装配，报错是「找不到 X 的 bean」。
        // 那条报错指向装配，读者得自己去 yml 里找原始拼写；这里把它提前钉在配置文本上。
        // ⚠ store-adapter 的取值集合本轮**从 {mock, feign} 收窄到 {feign}**：mock 那一侧的装配
        //    （infrastructure/mock）已随 T4b 删除，`mock` 不再是一个「代码认识」的取值。
        assertThat(String.valueOf(orderSection().get("store-adapter"))).isEqualTo(FEIGN);
        assertThat(String.valueOf(orderSection().get("repository"))).isIn("jdbc", "memory");
    }

    @Test
    @DisplayName("配置窗口与重试上限是正数（0 或负值会让幂等与重试静默失效）")
    void numericConfigIsSane() {
        assertThat(properties.getIdempotencyWindowSeconds()).isPositive();
        assertThat(properties.getOrderNoMaxRetry()).isPositive();
    }

    @Test
    @DisplayName("store-adapter 键缺失 → 仍装配 feign 侧（matchIfMissing 已随 mock 装配删除翻到 feign 侧）")
    void missingStoreAdapterKeyFallsBackToTheFeignAssembly() {
        // ⚠ 阶段一的默认值在 mock 侧（那时 mock 是**唯一存在**的装配），本轮 mock 装配被删除、
        //    默认值随之翻面（todo 残留 9）。若哪天有人删掉 feign 侧的 matchIfMissing 而没给出别的答案，
        //    本用例红——那正是「默认值无人守」的缺口。application.yml 仍显式写着该键，故这是兜底格、不是主路径。
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            assertThat(context.getEnvironment().containsProperty(STORE_ADAPTER_KEY))
                    .as("本格验的是「键缺失」，故上下文里必须确实没有这个键")
                    .isFalse();
            context.register(StoreFeignAdapterConfiguration.class, StoreClientTestDoubleConfiguration.class);
            context.refresh();

            assertThat(context.getBeanNamesForType(GoodsQueryPort.class)).hasSize(1);
            assertThat(context.getBeanNamesForType(StockPort.class)).hasSize(1);
        }
    }

    @Test
    @DisplayName("store-adapter 写错取值 → 一个 bean 都不装配、取用即找不到（含已作废的 mock）")
    void wrongStoreAdapterValueAssemblesNothing() {
        // ⚠ 本格是 MockStoreConfigurationTest#otherAdapterValueDisablesTheMockBeans 的继任者：
        //    那个哨兵随 mock 装配一起删掉后，「写错取值 → 零 bean → 启动失败」这条断言一度无人守。
        //    ⚠ `mock` 是**曾经的合法值**：它在 T4b 被作废，但仍是最可能被照旧文档写出来的那个错值，
        //    故与拼错（mockk）/ 空串并列——三者都必须落到同一个出口。
        for (String wrong : new String[]{"mock", "mockk", ""}) {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources()
                        .addFirst(new MapPropertySource("wrong-store-adapter",
                                Map.of(STORE_ADAPTER_KEY, wrong)));
                context.register(StoreFeignAdapterConfiguration.class, StoreClientTestDoubleConfiguration.class);
                context.refresh();

                assertThat(context.getBeanNamesForType(GoodsQueryPort.class))
                        .as("store-adapter=%s 不该装配出任何下游端口", wrong).isEmpty();
                assertThat(context.getBeanNamesForType(StockPort.class))
                        .as("store-adapter=%s 不该装配出任何下游端口", wrong).isEmpty();
                // 上下文能起来（条件不匹配只是不建 bean，不是报错），**取用**时才炸——
                // 这正是 application.yml 里写的那句「启动即报找不到 bean」，而非悄悄接单。
                assertThatThrownBy(() -> context.getBean(GoodsQueryPort.class))
                        .as("store-adapter=%s 时取用该端口必须失败", wrong)
                        .isInstanceOf(NoSuchBeanDefinitionException.class);
            }
        }
    }

    @Test
    @DisplayName("开关真的指到了真实 store 域那份装配：容器里的两个端口就是 feign 适配器")
    void storeAdapterSwitchPointsToTheFeignAssembly() {
        assertThat(goodsQueryPort).isInstanceOf(GoodsQueryAdapter.class);
        assertThat(stockPort).isInstanceOf(StockAdapter.class);
    }

    // ── 端到端：装配层与行为层没有断裂 ──────────────────────────────────────────

    @Test
    @DisplayName("装配出的协调器用替身下游跑通一单（拆单 / 流水线 / 库存 / 落库全链路）")
    void assembledCoordinatorCreatesAnOrder() {
        AtomicInteger available = givenSellableSkuWithStock(5);
        OrderCreateCommand command = new OrderCreateCommand(11L, OrderSource.DIRECT, ADDRESS, "req-wiring",
                List.of(new OrderCreateCommand.Line(SKU_ID, 2)));

        List<OrderModel> created = coordinator.create(command);

        assertThat(created).hasSize(1);
        OrderModel order = created.get(0);
        assertThat(order.getStoreId()).isEqualTo(STORE_ID);
        assertThat(order.getStoreName()).isEqualTo("示例店铺");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.isSealed()).isTrue();
        assertThat(order.getTotalQuantity()).isEqualTo(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getOrderNo()).hasSize(18);

        // 该单从替身下游一路取到快照、扣掉库存——「装配指向 store 域」在行为上成立
        verify(storeClient, times(1)).deductStock(any());
        assertThat(available).hasValue(3);
        // 顺带覆盖真适配器的 available（store 域没有单独的可售库存端点，它走同一条快照批量接口）
        assertThat(stockPort.available(SKU_ID)).isEqualTo(3);
        assertThat(orderRepository.count()).isEqualTo(1);

        // 同一个请求重放 → 装配链路上的两级幂等都生效（返回同一笔，且没有第二次扣减）
        assertThat(coordinator.create(command)).singleElement().isSameAs(order);
        assertThat(available).hasValue(3);
        verify(storeClient, times(1)).deductStock(any());
    }

    // ── 测试替身的行为（本类只调到「够跑通一单」的程度） ─────────────────────────────

    /**
     * 把 store 域替身调成「这一个 SKU 可购买、可售量为 {@code stock}」
     *
     * <p>替身自己维护可售量：够才扣、扣成功才减、不够返回 {@code false}——与 store 域那条
     * {@code UPDATE ... SET stock = stock - ? WHERE stock >= ?} 的**影响行数**语义对齐
     * （本类的目的是验装配，并发与真的超卖防护由 store 域的数据库承担）。</p>
     *
     * @param stock 初始可售量
     * @return 替身手里的可售量（断言用）
     */
    private AtomicInteger givenSellableSkuWithStock(int stock) {
        AtomicInteger available = new AtomicInteger(stock);
        when(storeClient.tradeSkuSnapshotBatch(any())).thenAnswer(invocation -> {
            StoreGoodsSkuBatchQueryDTO query = invocation.getArgument(0);
            return query.getSkuIds().stream()
                    .filter(SKU_ID::equals)
                    .map(skuId -> snapshot(available.get()))
                    .toList();
        });
        when(storeClient.deductStock(any())).thenAnswer(invocation -> {
            StoreStockDeductDTO dto = invocation.getArgument(0);
            int current = available.get();
            return current >= dto.getQuantity() && available.compareAndSet(current, current - dto.getQuantity());
        });
        return available;
    }

    /**
     * 一条交易视角的快照：店铺已审核 + SPU / SKU 都已上架 + 未被锁定（四个开关全开），可售量由调用方给。
     *
     * <p>⚠ 这里给的是 store 域的**原始取值**（{@code shopStatus=2} / {@code skuShelfStatus=1}…），
     * 不是布尔开关——「哪个取值算通过」正由 {@link GoodsQueryAdapter} 解释，本类顺带验了那次解释。</p>
     */
    private static StoreGoodsSkuSnapshotVO snapshot(int availableStock) {
        StoreGoodsSkuSnapshotVO vo = new StoreGoodsSkuSnapshotVO();
        vo.setSkuId(SKU_ID);
        vo.setSpuId(SPU_ID);
        vo.setStoreId(STORE_ID);
        vo.setStoreName("示例店铺");
        vo.setSpuName("商品910");
        vo.setMainImage("http://img/910.png");
        SpecAttr color = new SpecAttr();
        color.setSpec("颜色");
        color.setValue("黑");
        vo.setSpecAttrs(List.of(color));
        vo.setPrice(new BigDecimal("10.00"));
        vo.setShopStatus(2);
        vo.setSpuShelfStatus(1);
        vo.setSkuShelfStatus(1);
        vo.setLockStatus(0);
        vo.setAvailableStock(availableStock);
        return vo;
    }

    // ── 真实配置的读取 ─────────────────────────────────────────────────────────

    /**
     * 把真实 {@code application.yml} 里的 {@code panoramic.trade.order} 喂进测试上下文的环境
     *
     * <p>⚠ 放在**最后**（{@code addLast}）：将来某个用例要临时改一项配置时，
     * {@code @TestPropertySource} 之类的来源能盖过它，而不必来改这里。</p>
     *
     * <p>⚠ <b>必须摊平成「索引键 + 字符串值」</b>（{@code steps[0]=goods-check}），不能把
     * snakeyaml 解出的 {@code List}/{@code Integer} 原样塞进去：Spring Boot 的 YAML 加载器本来就是
     * 这么摊平的，而 {@code MapPropertySource} 直接放一个 List 会让按类型绑定退化——
     * {@code List<OrderStatus> status-flow} 里的元素会保持 {@code String} 原样，
     * 直到 {@code OrderStatusFlow} 构造时抛 {@code ClassCastException}（装配期炸，看着像配置写错，
     * 其实是喂进来的形状不对）。</p>
     */
    public static class RealApplicationYmlInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            Map<String, Object> flattened = new LinkedHashMap<>();
            flatten(PREFIX, orderSection(), flattened);
            context.getEnvironment().getPropertySources()
                    .addLast(new MapPropertySource("real-application-yml", flattened));
        }

        /** 摊平成 Boot YAML 加载器同款的形状：嵌套段用 {@code .} 相连，列表用 {@code [i]} 下标，值一律字符串 */
        @SuppressWarnings("unchecked")
        private static void flatten(String prefix, Map<String, Object> section, Map<String, Object> sink) {
            section.forEach((key, value) -> {
                String name = prefix + key;
                if (value instanceof Map<?, ?> nested) {
                    flatten(name + ".", (Map<String, Object>) nested, sink);
                } else if (value instanceof List<?> list) {
                    // ⚠ 空列表**不能什么都不放**：Boot 的加载器把空列表节点（{@code []}）构造为一个空串标量，
                    //    绑定器再把它转成空集合——故这里也要写成空串，否则键整个消失，与真实加载路径不同。
                    //    ⚠ 当前 order 段里**没有**空列表节点（旧版 status-flow 的入口状态写成 {@code []} 时
                    //    是唯一的触发点），这一支留着是为了与 Boot 的加载路径保持同形，别再往 order 段里
                    //    加空列表而指望它俩仍然一致。
                    if (list.isEmpty()) {
                        sink.put(name, "");
                        return;
                    }
                    for (int i = 0; i < list.size(); i++) {
                        sink.put(name + "[" + i + "]", String.valueOf(list.get(i)));
                    }
                } else {
                    sink.put(name, String.valueOf(value));
                }
            });
        }
    }

    private static final String PREFIX = "panoramic.trade.order.";

    /**
     * 解析真实配置文件里的 {@code panoramic.trade.order} 段
     *
     * <p>文件读不到就直接失败（不静默跳过）：一个「找不到文件就跳过」的漂移守卫等于没有守卫——
     * 它会在最需要它的那天（有人搬了目录、换了工作目录）悄悄失去作用。</p>
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> orderSection() {
        if (!Files.exists(APPLICATION_YML)) {
            throw new IllegalStateException("找不到真实配置文件：" + APPLICATION_YML.toAbsolutePath()
                    + "（本用例要求在模块根目录下运行——Maven surefire 的默认工作目录即是模块根）");
        }
        try {
            String text = MAVEN_PLACEHOLDER.matcher(Files.readString(APPLICATION_YML, StandardCharsets.UTF_8))
                    .replaceAll("\"maven-filtered\"");
            Map<String, Object> root = new Yaml().load(text);
            Map<String, Object> panoramic = (Map<String, Object>) root.get("panoramic");
            Map<String, Object> trade = (Map<String, Object>) panoramic.get("trade");
            return (Map<String, Object>) trade.get("order");
        } catch (IOException e) {
            throw new UncheckedIOException("读取 " + APPLICATION_YML + " 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> configuredSteps() {
        return (List<String>) orderSection().get("steps");
    }

    /**
     * 真实 yml 里声明的主链（**有序**：数组顺序即先后）
     *
     * <p>⚠ 每一项都是**枚举常量名原样**（{@code PENDING_PAYMENT}）：经
     * {@code ConfigurationPropertyName} 的 {@code Form.ORIGINAL} 取值时，下划线在大写形式里是放行的。</p>
     */
    @SuppressWarnings("unchecked")
    private static List<OrderStatus> configuredStatusChain() {
        List<String> declared = (List<String>) orderSection().get("status-flow");
        return declared.stream().map(OrderStatus::valueOf).toList();
    }

    /**
     * 两个**结束过程**的落点：它们不在主链上（yml 的 {@code status-flow} 里不该出现它们），
     * 到达它们的前置状态写在 {@code OrderModel} 的动作方法里。
     *
     * <p>⚠ 这里照抄一份是为了当**期望值**（映射到断言上），不是第二份定义：定义只有
     * {@code OrderStatusFlow} 里的 {@code ENDING_TARGETS} 一处，改了它而不改这里，本类就会红。</p>
     */
    private static final Set<OrderStatus> ENDING_TARGETS = Set.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    private static long configuredWindowSeconds() {
        return ((Number) orderSection().get("idempotency-window-seconds")).longValue();
    }

    private static int configuredMaxRetry() {
        return ((Number) orderSection().get("order-no-max-retry")).intValue();
    }
}
