package com.panoramic.trade.order.infrastructure.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.domain.port.StockPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * mock 商品脚手架的**数据面**：快照文件能加载、取值被正确解释成开关、且它声称覆盖的分支真的有数据点。
 *
 * <p>⚠ 为什么单独一个类，而不是塞进 {@code OrderDomainWiringTest}：那个类的每例都会清空内存端口
 * （用例之间互不残留），而本类要验的恰恰是「{@link MockStoreConfiguration} 灌进去的那份数据长什么样」。
 * 两者的前置状态正好相反，合成一个类只会让 {@code @BeforeEach} 变成「清不清」的条件判断。</p>
 *
 * <p>⚠ 本类钉的是**断言，不是散文**：{@code MockStoreConfiguration} 的类注释写着「这份快照覆盖到哪」，
 * 而注释不会因为数据变样而变红。这里把那些话逐条换成断言——数据被重新导出、恰好丢了某个反例行时，
 * 测试会红，而不是让一段过时的说明继续躺在代码里。</p>
 */
@SpringJUnitConfig(classes = {MockStoreConfiguration.class, MockStoreConfigurationTest.TestSupportConfiguration.class})
@TestPropertySource(properties = MockStoreConfigurationTest.ADAPTER_IS_MOCK)
class MockStoreConfigurationTest {

    /** 本类只验 {@code mock} 这一侧；开关本身的「写错就不装配」由 {@link #otherAdapterValueDisablesTheMockBeans()} 验 */
    static final String ADAPTER_IS_MOCK = "panoramic.trade.order.store-adapter=mock";

    @Autowired
    private MockStoreData data;

    @Autowired
    private InMemoryGoodsQueryPort goodsQueryPort;

    @Autowired
    private InMemoryStockPort stockPort;

    /** mock 的两个端口 bean 要 {@code ObjectMapper} 与 {@code Clock}，测试上下文缺 Boot 自动配置，得自己给 */
    @Configuration
    static class TestSupportConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-09-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }

    private Map<Long, SkuSnapshot> snapshots() {
        List<Long> skuIds = data.skus().stream().map(MockStoreData.Sku::skuId).toList();
        Map<Long, SkuSnapshot> all = goodsQueryPort.mapBySkuIds(skuIds);
        assertThat(all).hasSameSizeAs(skuIds);   // 每一行都要能查到，少一行说明 put 时被谁吃掉了
        return all;
    }

    /** 「可购买」的四个开关全开：店铺已审核 + SPU 上架 + SKU 上架 + 未被锁定 */
    private static boolean visible(SkuSnapshot snapshot) {
        return snapshot.shopApproved() && snapshot.spuOnShelf() && snapshot.skuOnShelf() && !snapshot.spuLocked();
    }

    // ── 文件本身 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("快照能加载，且带齐「从哪来 + 与真库差在哪」两项凭据")
    void snapshotCarriesItsProvenance() {
        assertThat(data.skus()).isNotEmpty();
        // 这三项不是装饰：exportedAt 说明数据有多旧（尤其是库存）、source 说明哪张表导的、
        // note 说明与真库的已知差异。缺了它们，这份数据就变成「不知道谁塞进来的假数据」。
        assertThat(data.exportedAt()).isNotBlank();
        assertThat(data.source()).isNotBlank();
        assertThat(data.note()).isNotBlank();
    }

    @Test
    @DisplayName("规格以「数组」落盘、以「表」进内存：转换真的发生了（两边都写 Map 也能编译，但永远是空表）")
    void specAttrsAreConvertedFromArrayToMap() {
        List<SkuSnapshot> withAttrs = snapshots().values().stream()
                .filter(snapshot -> !snapshot.specAttrs().isEmpty())
                .toList();

        assertThat(withAttrs).isNotEmpty();
        // 键是规格名、值是该行的取值；若错把 {"spec":..,"value":..} 整个塞进来，键会变成 "spec"/"value"
        assertThat(withAttrs).allSatisfy(snapshot ->
                assertThat(snapshot.specAttrs()).doesNotContainKeys("spec", "value"));
    }

    // ── 覆盖了哪些分支（对应 MockStoreConfiguration 类注释里那份清单） ────────────

    @Test
    @DisplayName("覆盖「多店拆单」：快照里不止一家店铺（一单一店的拆单路径才有得验）")
    void coversMultipleStores() {
        // ⚠ 数的是**去重后的店铺数**，不是行数：20 行全在一家店时这条断言必须红，
        // 而「行数 ≥ 2」会照常通过——那正是「拆单路径没数据点」却看不出来的情形
        assertThat(snapshots().values().stream().map(SkuSnapshot::storeId).distinct())
                .as("快照里的店铺数")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("覆盖「SKU 下架」与「SPU 被平台锁定」两条拒绝分支（各至少一行）")
    void coversSkuOffShelfAndLockedBranches() {
        List<SkuSnapshot> all = List.copyOf(snapshots().values());

        assertThat(all).anySatisfy(snapshot -> {
            assertThat(snapshot.shopApproved()).isTrue();        // 拒绝的原因必须**只是** SKU 下架
            assertThat(snapshot.spuOnShelf()).isTrue();
            assertThat(snapshot.spuLocked()).isFalse();
            assertThat(snapshot.skuOnShelf()).isFalse();
        });
        assertThat(all).anySatisfy(snapshot -> {
            assertThat(snapshot.spuLocked()).isTrue();           // 锁定优先于上下架，故不再限其它开关
        });
    }

    @Test
    @DisplayName("覆盖「上架但零库存」：可购买的行里有一行库存为 0（否则「库存不足」只能靠假数据验）")
    void coversOutOfStockBranch() {
        Map<Long, SkuSnapshot> all = snapshots();

        assertThat(all.values().stream().filter(MockStoreConfigurationTest::visible).toList())
                .anySatisfy(snapshot -> assertThat(stockPort.available(snapshot.skuId())).isZero());
        // 可购买且真有货的行也必须存在，否则「下单成功」这条主路径在真实快照上跑不起来
        assertThat(all.values().stream().filter(MockStoreConfigurationTest::visible).toList())
                .anySatisfy(snapshot -> assertThat(stockPort.available(snapshot.skuId())).isPositive());
    }

    @Test
    @DisplayName("库存初值取自快照行（内存库存是「一次运行内的账」，起点必须与导出值一致）")
    void stockStartsFromTheSnapshot() {
        for (MockStoreData.Sku sku : data.skus()) {
            assertThat(stockPort.available(sku.skuId()))
                    .as("skuId=%s 的库存初值", sku.skuId())
                    .isEqualTo(sku.stock() == null ? 0 : sku.stock());
        }
    }

    // ── 空数据是「快照坏了」，不是「没有商品」 ──────────────────────────────────

    @Test
    @DisplayName("开关写成别的值 → 两个端口 bean 一个都不装配（起不来，而不是悄悄用过期快照接单）")
    void otherAdapterValueDisablesTheMockBeans() {
        String key = "panoramic.trade.order.store-adapter";
        List<Map<String, Object>> cases = List.of(
                Map.of(key, "feign"),                       // 将来那个真实实现（此时该由别的配置类提供端口）
                Map.of(key, "mockk"),                       // 典型手误
                Map.of(key, ""));                           // 键在、值为空

        for (Map<String, Object> properties : cases) {
            assertThat(beansOf(properties))
                    .as("开关为 %s 时不应装配任何端口 bean", properties)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("阶段一：键缺失 = mock（默认值在 mock 侧；阶段二 T4b 本类删除时该默认翻到 feign 侧）")
    void missingKeyFallsBackToTheStageOneMock() {
        // ⚠ 这一格**与上面那条相反**，且是刻意的：阶段一只有 mock 这一个装配存在，缺失值不可能落到别人身上；
        //    阶段二本类被删除、feign 装配成为唯一存在者并接过 matchIfMissing（todo 残留 9）。
        //    若哪天有人删掉这里的 matchIfMissing 而没翻面，本用例红——那正是「默认值无人守」的缺口。
        //    application.yml 仍然显式写着 store-adapter: mock，所以这一格是兜底、不是主路径。
        assertThat(beansOf(Map.of()))
                .as("键缺失时应按 mock 装配（阶段一）")
                .containsExactlyInAnyOrder(GoodsQueryPort.class, StockPort.class);
    }

    /**
     * 用给定的开关属性刷新一个最小上下文，返回它装配出来的**端口 bean 类型**
     *
     * <p>两个端口 bean 一起数：只装配一个同样是错的（goods-check 与 stock-check 各要一个）。</p>
     */
    private static List<Class<?>> beansOf(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("switch", properties));
            context.register(MockStoreConfiguration.class, TestSupportConfiguration.class);
            context.refresh();
            return Stream.<Class<?>>of(GoodsQueryPort.class, StockPort.class)
                    .filter(type -> context.getBeanNamesForType(type).length > 0)
                    .toList();
        }
    }

    @Test
    @DisplayName("空数据一律启动失败：静默接受的话，每次下单都会以「商品不存在」被拒（报错指向顾客入参）")
    void emptyDataFailsFast() {
        assertThatThrownBy(() -> new MockStoreData("2026-09-21", "source", "note", List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mock 商品数据为空");
        assertThatThrownBy(() -> new MockStoreData("2026-09-21", "source", "note", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mock 商品数据为空");
    }
}
