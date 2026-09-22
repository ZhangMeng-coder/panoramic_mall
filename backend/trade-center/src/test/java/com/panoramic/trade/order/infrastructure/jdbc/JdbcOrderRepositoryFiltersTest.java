package com.panoramic.trade.order.infrastructure.jdbc;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.infrastructure.entity.TradeOrder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 分页共用的筛选条件（{@code JdbcOrderRepository#applyCommonFilters}）：**可选筛选项翻成 SQL 条件**。
 *
 * <p>⚠ 为什么只钉这一小段、而不整条分页链路：本类的其余部分要么在内存仓储里有等价物
 * （幂等键语义），要么只能在真库上验。只有这段是「契约可选字段 → SQL 条件」的**唯一**翻译点，
 * 各调用方的分页（顾客 / 商户 / 管理端）全走它，而它没有任何内存实现可对照。</p>
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
}
