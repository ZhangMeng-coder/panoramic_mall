package com.panoramic.trade.order.infrastructure.jdbc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.infrastructure.entity.TradeOrder;
import com.panoramic.trade.order.infrastructure.service.TradeOrderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 分页共用的筛选条件（{@code JdbcOrderRepository#applyCommonFilters}）：**可选筛选项翻成 SQL 条件**。
 *
 * <p>⚠ 为什么只钉这一小段、而不整条读链路：本类的其余部分要么在内存仓储里有等价物
 * （幂等键语义），要么只能在真库上验。只有这段是「契约可选字段 → SQL 条件」的**唯一**翻译点——
 * 分页（`pageOrders`）与详情（`findOrder`，写路径的前置读）都经 {@code filterWrapper} 走它，
 * 而它没有任何内存实现可对照。</p>
 *
 * <p>⚠ <b>两端各从自己的入口钉</b>：分页侧直接调 {@code applyCommonFilters}；详情侧
 * **经 {@code findOrder}**（捕获它真正下发的 Wrapper，见本类末尾的假 service）——
 * 直接调 helper 时，把 {@code findOrder} 改回自己那套条件块，用例照样绿。</p>
 *
 * <p>⚠ 它守的是一个**纯默认路径**的缺陷：{@code eq(condition, col, value)} 的 {@code value}
 * 无条件求值，写成 {@code query.status().name()} 时「不筛状态」就 NPE 成 500。
 * 2026-09-22 的 T15 运行验证里各侧分页全被打成 500，就是这一处——故这里同时断言
 * 「不抛」与「SQL 里确实没有 status 条件」，只断言不抛会漏掉「条件恒 false」的改法。</p>
 *
 * <p>⚠ 作用域（{@code customerId} / {@code storeId}）是同一种「传了就筛、没传就不限定」，
 * 故两对用例（筛 / 不筛）与 orderNo / status 同形——它们是**同一处翻译**的四个字段。</p>
 *
 * <p>⚠ 表元数据要自己初始化（本类跑在纯 JUnit 下，没有 Spring 上下文，MP 的 lambda 缓存是空的，
 * 否则 {@code TradeOrder::getStatus} 解析不出列名直接报 {@code can not find lambda cache}）。</p>
 */
class JdbcOrderRepositoryFiltersTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TradeOrder.class);
    }

    @Test
    @DisplayName("status = null（不筛）不抛异常，且 SQL 段里没有 status 条件")
    void nullStatusIsSkipped() {
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.lambdaQuery();
        assertThatCode(() -> JdbcOrderRepository.applyCommonFilters(wrapper, query(null, null, null, null)))
                .doesNotThrowAnyException();
        assertThat(wrapper.getSqlSegment()).doesNotContain("status");
    }

    @Test
    @DisplayName("status = PAID 时条件进 SQL，取值走占位符（不是拼字符串）")
    void nonNullStatusBecomesCondition() {
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.lambdaQuery();
        JdbcOrderRepository.applyCommonFilters(wrapper, query(null, OrderStatus.PAID, null, null));
        assertThat(wrapper.getSqlSegment()).contains("status =").contains("MPGENVAL");
    }

    @Test
    @DisplayName("orderNo = null（不筛）不进条件，非 null 时按精确匹配进条件")
    void orderNoIsOptional() {
        LambdaQueryWrapper<TradeOrder> blank = Wrappers.lambdaQuery();
        JdbcOrderRepository.applyCommonFilters(blank, query(null, null, null, null));
        assertThat(blank.getSqlSegment()).doesNotContain("order_no");

        LambdaQueryWrapper<TradeOrder> exact = Wrappers.lambdaQuery();
        JdbcOrderRepository.applyCommonFilters(exact, query("202609221200000001", null, null, null));
        assertThat(exact.getSqlSegment()).contains("order_no =");
    }

    @Test
    @DisplayName("作用域传了就按它收窄：customerId / storeId 各自进 SQL 条件")
    void scopesBecomeConditionsWhenPresent() {
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.lambdaQuery();
        assertThatCode(() -> JdbcOrderRepository.applyCommonFilters(wrapper, query(null, null, 7L, 5L)))
                .doesNotThrowAnyException();
        assertThat(wrapper.getSqlSegment()).contains("customer_id =").contains("store_id =");
    }

    @Test
    @DisplayName("作用域不传即不限定（管理端全量视角）：SQL 段里没有 customer_id / store_id")
    void scopesAreSkippedWhenAbsent() {
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.lambdaQuery();
        JdbcOrderRepository.applyCommonFilters(wrapper, query(null, null, null, null));
        assertThat(wrapper.getSqlSegment()).doesNotContain("customer_id").doesNotContain("store_id");
    }

    @Test
    @DisplayName("详情与分页走同一份条件构造：findOrder **真正下发的**条件里带作用域")
    void detailReusesTheSameFilterConstruction() {
        LambdaQueryWrapper<TradeOrder> wrapper = detailWrapperOfFindOrder(
                OrderQuery.forDetail("202609221200000001", 7L, 5L));

        assertThat(wrapper.getSqlSegment())
                .contains("order_no =")
                .contains("customer_id =")
                .contains("store_id =");
    }

    @Test
    @DisplayName("详情不传作用域即不限定：findOrder 下发的条件里没有 customer_id / store_id（管理端全量视角）")
    void detailWithoutScopeIsUnrestricted() {
        LambdaQueryWrapper<TradeOrder> wrapper = detailWrapperOfFindOrder(
                OrderQuery.forDetail("202609221200000001", null, null));

        assertThat(wrapper.getSqlSegment())
                .contains("order_no =")
                .doesNotContain("customer_id")
                .doesNotContain("store_id");
    }

    @Test
    @DisplayName("详情单号不能为空：空白单号在工厂处就被拒（早失败）")
    void detailRequiresOrderNo() {
        assertThatThrownBy(() -> OrderQuery.forDetail("   ", 7L, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("订单号");
    }

    @Test
    @DisplayName("单号必填是 findOrder 的不变量：绕过工厂直接 new 也拦得住，且一次查询都不发")
    void findOrderRequiresOrderNoBeyondTheFactory() {
        AtomicReference<LambdaQueryWrapper<TradeOrder>> captured = new AtomicReference<>();
        JdbcOrderRepository repository = repositoryCapturingInto(captured);

        // ⚠ 这份查询是合法 Java（记录是 public、构造器不拦单号）：只守 forDetail 的话，
        //    「按单号取」这一项条件会被 applyCommonFilters 跳过 → 变成「取该顾客的任意一笔」
        assertThatThrownBy(() -> repository.findOrder(
                new OrderQuery(null, OrderStatus.PAID, 7L, null, 1, 1)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("订单号");
        assertThat(captured.get())
                .as("护栏在建条件 / 发查询之前就拦下：不该有任何一次 selectOne")
                .isNull();
    }

    @Test
    @DisplayName("无论筛选如何，排序恒为 id 倒序（各侧分页都是下单倒序）")
    void orderByIdDescAlwaysApplied() {
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.lambdaQuery();
        JdbcOrderRepository.applyCommonFilters(wrapper, query(null, null, null, null));
        assertThat(wrapper.getSqlSegment()).containsIgnoringCase("order by id desc");
    }

    /**
     * 分页查询条件（六个字段里只有四个参与筛选；分页参数与本类断言无关，固定 1 / 10）
     */
    private static OrderQuery query(String orderNo, OrderStatus status, Long customerId, Long storeId) {
        return new OrderQuery(orderNo, status, customerId, storeId, 1, 10);
    }

    /**
     * 走**真实入口** {@code findOrder} 取出它下发的 {@link LambdaQueryWrapper}
     *
     * <p>⚠ 为什么不经 {@code JdbcOrderRepository.filterWrapper(...)} 直接构造：那样钉的是 helper 本身。
     * 把 {@code findOrder} 改回它自己那套条件块（= 本轮要消灭的「第二处翻译」，也是「漏传作用域 =
     * 静默取任意一笔」的洞），helper 还在、用例照样绿。故这里从**消费方**出发：装配一个只接住
     * {@code getOne} 那个 Wrapper 实参的假 service，看 {@code findOrder} 到底下发了什么条件。</p>
     *
     * <p>⚠ 假 service 用 {@link Proxy} 而不是手写实现：{@code IService} 的抽象方法有几十个，
     * 逐个空实现只会淹没本类要表达的那一件事（本类不需 Mockito，仓内也没有先例）。</p>
     */
    private static LambdaQueryWrapper<TradeOrder> detailWrapperOfFindOrder(OrderQuery query) {
        AtomicReference<LambdaQueryWrapper<TradeOrder>> sink = new AtomicReference<>();
        JdbcOrderRepository repository = repositoryCapturingInto(sink);

        assertThat(repository.findOrder(query)).isEmpty();
        assertThat(sink.get()).as("findOrder 必须真的按条件查了一次").isNotNull();
        return sink.get();
    }

    /**
     * 详情仓储：{@code orderService} 是个只记录「收到过哪个 Wrapper」的假实现，其余一律空返回
     *
     * <p>⚠ 它顺带保证用例**不碰真库**：所有方法都返回 null / false，{@code getOne} 得到 null 即
     * {@code Optional.empty()}，组装与对账那一段根本不会执行。</p>
     */
    private static JdbcOrderRepository repositoryCapturingInto(AtomicReference<LambdaQueryWrapper<TradeOrder>> sink) {
        TradeOrderService fakeOrderService = (TradeOrderService) Proxy.newProxyInstance(
                JdbcOrderRepositoryFiltersTest.class.getClassLoader(),
                new Class<?>[]{TradeOrderService.class},
                (proxy, method, args) -> {
                    if (args != null) {
                        for (Object arg : args) {
                            if (arg instanceof LambdaQueryWrapper<?> wrapper) {
                                sink.set(asOrderWrapper(wrapper));
                            }
                        }
                    }
                    return defaultValueOf(method.getReturnType());
                });
        // 其余五个基类 service / 状态机 / ObjectMapper / Clock 在详情这条路上一律不碰（只有 getOne 被调到）
        return new JdbcOrderRepository(null, null, fakeOrderService, null, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<TradeOrder> asOrderWrapper(Object raw) {
        return (LambdaQueryWrapper<TradeOrder>) raw;
    }

    private static Object defaultValueOf(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }
}
