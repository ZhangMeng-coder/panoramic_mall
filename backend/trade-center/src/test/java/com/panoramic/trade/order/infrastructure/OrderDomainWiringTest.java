package com.panoramic.trade.order.infrastructure;

import com.panoramic.trade.order.application.OrderCreateCommand;
import com.panoramic.trade.order.application.OrderCreateCoordinator;
import com.panoramic.trade.order.application.OrderCreatePipeline;
import com.panoramic.trade.order.application.OrderCreateStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryOrderRepository;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import com.panoramic.trade.order.infrastructure.mock.MockStoreConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

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
 * {@code store-adapter} 保持真实值 {@code mock}，故本类要一并加载 {@link MockStoreConfiguration}
 * ——商品 / 库存端口本来就由它提供，这也顺带验了「开关真的指到了那份快照」。</p>
 */
@SpringJUnitConfig(classes = {OrderDomainConfiguration.class, MockStoreConfiguration.class,
        OrderDomainWiringTest.TestObjectMapperConfiguration.class},
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

    private static final Long SKU_ID = 910L;
    private static final Long STORE_ID = 7L;

    /** 收货地址：本类用例都不关心地址内容，取一份合法值即可 */
    private static final OrderAddress ADDRESS =
            new OrderAddress("张三", "13800000000", "浙江省杭州市西湖区", "文一西路 969 号 1 幢 101 室");

    /** 测试上下文缺 Boot 自动配置，{@code ObjectMapper} 得自己给（mock 快照的解析要用它） */
    @Configuration
    static class TestObjectMapperConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
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
    private InMemoryGoodsQueryPort goodsQueryPort;

    @Autowired
    private InMemoryStockPort stockPort;

    @Autowired
    private InMemoryOrderRepository orderRepository;

    @BeforeEach
    void resetInMemoryState() {
        // 内存端口是单例，用例之间会互相残留；每例从空仓库、空商品表开始
        orderRepository.clear();
        goodsQueryPort.clear();
    }

    // ── 漂移守卫：真实 yml 与装配出的东西逐项对账 ────────────────────────────────

    @Test
    @DisplayName("配置被真实读入：steps / status-flow / 窗口 / 重试上限都与 application.yml 一致")
    void realConfigIsBound() {
        assertThat(properties.getSteps()).containsExactlyElementsOf(configuredSteps());
        assertThat(properties.getStatusFlow()).containsExactlyElementsOf(configuredStatusFlow());
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
    @DisplayName("② status-flow 与 OrderStatus 的全部常量集合相等（枚举两侧不漂移）")
    void statusFlowCoversAllEnumConstants() {
        Set<OrderStatus> configured = new HashSet<>(configuredStatusFlow());
        Set<OrderStatus> allConstants = EnumSet.allOf(OrderStatus.class);

        assertThat(configured).containsExactlyInAnyOrderElementsOf(allConstants);
        // 装配出来的状态机同样覆盖全部常量，且顺序就是配置里的顺序（顺序来自配置，不写死在代码里）
        assertThat(statusFlow.statuses()).containsExactlyInAnyOrderElementsOf(allConstants);
        assertThat(statusFlow.statuses()).containsExactlyElementsOf(configuredStatusFlow());
    }

    @Test
    @DisplayName("③ 装配出的流水线执行顺序 === 配置里 steps 的顺序")
    void pipelineOrderMatchesConfiguration() {
        assertThat(pipeline.stepNames()).containsExactlyElementsOf(configuredSteps());
    }

    @Test
    @DisplayName("两个开关都写实了取值，且取值是代码认识的那几个（不设默认值 → 写错即起不来）")
    void repositorySwitchIsOneOfTheSupportedValues() {
        // ⚠ 这两个键刻意没有默认值：缺失或写错时对应 bean 一个都不装配，报错是「找不到 X 的 bean」。
        // 那条报错指向装配，读者得自己去 yml 里找原始拼写；这里把它提前钉在配置文本上。
        assertThat(String.valueOf(orderSection().get("store-adapter"))).isIn("mock", "feign");
        assertThat(String.valueOf(orderSection().get("repository"))).isIn("jdbc", "memory");
    }

    @Test
    @DisplayName("配置窗口与重试上限是正数（0 或负值会让幂等与重试静默失效）")
    void numericConfigIsSane() {
        assertThat(properties.getIdempotencyWindowSeconds()).isPositive();
        assertThat(properties.getOrderNoMaxRetry()).isPositive();
    }

    // ── 端到端：装配层与行为层没有断裂 ──────────────────────────────────────────

    @Test
    @DisplayName("装配出的协调器用预置商品 + 库存跑通一单（拆单 / 流水线 / 库存 / 落库全链路）")
    void assembledCoordinatorCreatesAnOrder() {
        goodsQueryPort.put(InMemoryGoodsQueryPort.sellable(SKU_ID, STORE_ID, "示例店铺", "10.00"));
        stockPort.setStock(SKU_ID, 5);
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

        assertThat(stockPort.available(SKU_ID)).isEqualTo(3);
        assertThat(orderRepository.count()).isEqualTo(1);

        // 同一个请求重放 → 装配链路上的两级幂等都生效（返回同一笔）
        assertThat(coordinator.create(command)).singleElement().isSameAs(order);
        assertThat(stockPort.available(SKU_ID)).isEqualTo(3);
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

    @SuppressWarnings("unchecked")
    private static List<OrderStatus> configuredStatusFlow() {
        // yml 里写的是枚举常量名（kebab-case 的键，PascalCase 的值）
        return ((List<String>) orderSection().get("status-flow")).stream().map(OrderStatus::valueOf).toList();
    }

    private static long configuredWindowSeconds() {
        return ((Number) orderSection().get("idempotency-window-seconds")).longValue();
    }

    private static int configuredMaxRetry() {
        return ((Number) orderSection().get("order-no-max-retry")).intValue();
    }
}
