package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 流水线：顺序来自配置、配了不存在的步骤名即装配失败、配了不跑的步骤就真的不跑、某步抛错后续不执行。
 *
 * <p>⚠ 本类**不启 Spring**（裁定 D10）：流水线是「配置 × bean 列表」的纯函数，
 * 用假步骤（记录自己被调用过）就能把顺序钉死；起上下文只会把「测装配」和「测编排」混在一起。</p>
 *
 * <p>⚠ 「顺序」用例刻意让 bean 的传入顺序与配置顺序**相反**：若实现在某处偷懒按 bean 顺序跑，
 * 用例必须红——顺序的唯一来源是配置。</p>
 */
class OrderCreatePipelineTest {

    private static final LocalDateTime CREATE_TIME = LocalDateTime.of(2026, 9, 21, 12, 0, 0);

    /** 记录调用序的假步骤（可选：抛错 / 只记录） */
    private static final class RecordingStep implements OrderCreateStep {

        private final String name;
        private final List<String> calls;
        private final RuntimeException failure;

        private RecordingStep(String name, List<String> calls, RuntimeException failure) {
            this.name = name;
            this.calls = calls;
            this.failure = failure;
        }

        static RecordingStep ok(String name, List<String> calls) {
            return new RecordingStep(name, calls, null);
        }

        static RecordingStep failing(String name, List<String> calls) {
            return new RecordingStep(name, calls, new ServiceException(400, "步骤 " + name + " 失败"));
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void execute(OrderModel order) {
            calls.add(name);
            if (failure != null) {
                throw failure;
            }
        }
    }

    /** 只用来喂给流水线的最小订单（假步骤不碰它，故不需要 seal） */
    private static OrderModel anyOrder() {
        return OrderModel.open("202609211200000001", 11L, 7L, "示例店铺", OrderSource.DIRECT,
                "req-1", "fp-1", CREATE_TIME, List.of(new OrderLine(10L, 1)));
    }

    private static OrderProperties props(String... steps) {
        OrderProperties properties = new OrderProperties();
        properties.setSteps(List.of(steps));
        properties.setStatusFlow(List.of(OrderStatus.values()));
        properties.setIdempotencyWindowSeconds(300);
        properties.setOrderNoMaxRetry(5);
        return properties;
    }

    // ── 顺序来自配置 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("按配置顺序执行：bean 传入顺序与配置顺序相反，仍按配置跑")
    void executesInConfiguredOrder() {
        List<String> calls = new ArrayList<>();
        // bean 顺序刻意是 c、b、a；配置是 a、b、c
        List<OrderCreateStep> beans = List.of(
                RecordingStep.ok("c", calls), RecordingStep.ok("b", calls), RecordingStep.ok("a", calls));

        OrderCreatePipeline pipeline = new OrderCreatePipeline(beans, props("a", "b", "c"));
        pipeline.execute(anyOrder());

        assertThat(calls).containsExactly("a", "b", "c");
        assertThat(pipeline.stepNames()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("换一份步骤配置，同一个 bean 集合执行顺序跟着变（证明顺序只来自配置）")
    void orderComesFromConfigOnly() {
        List<String> calls = new ArrayList<>();
        List<OrderCreateStep> beans = List.of(
                RecordingStep.ok("a", calls), RecordingStep.ok("b", calls), RecordingStep.ok("c", calls));

        OrderCreatePipeline pipeline = new OrderCreatePipeline(beans, props("c", "a"));
        pipeline.execute(anyOrder());

        assertThat(calls).containsExactly("c", "a");
    }

    @Test
    @DisplayName("配置里没写的步骤就不跑（关掉一步即真的不执行）")
    void omittedStepIsNotExecuted() {
        List<String> calls = new ArrayList<>();
        List<OrderCreateStep> beans = List.of(
                RecordingStep.ok("goods-check", calls),
                RecordingStep.ok("stock-check", calls),
                RecordingStep.ok("price-compute", calls));

        OrderCreatePipeline pipeline = new OrderCreatePipeline(beans, props("goods-check", "price-compute"));
        pipeline.execute(anyOrder());

        assertThat(calls).containsExactly("goods-check", "price-compute");
        assertThat(pipeline.stepNames()).containsExactly("goods-check", "price-compute");
    }

    @Test
    @DisplayName("stepNames() 回不可变副本（外部改不动流水线的执行顺序）")
    void stepNamesAreImmutable() {
        List<String> calls = new ArrayList<>();
        OrderCreatePipeline pipeline = new OrderCreatePipeline(
                List.of(RecordingStep.ok("a", calls)), props("a"));

        assertThatThrownBy(() -> pipeline.stepNames().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── 装配期：配置与 bean 对不上就起不来 ──────────────────────────────────────

    @Test
    @DisplayName("配置里出现不存在的步骤名 → IllegalStateException，消息同时给出错的名字与可用的名字集合")
    void unknownStepNameRejected() {
        List<String> calls = new ArrayList<>();
        List<OrderCreateStep> beans = List.of(RecordingStep.ok("goods-check", calls));

        Throwable thrown = catchThrowable(() -> new OrderCreatePipeline(beans, props("goods-check", "risk-check")));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage()).contains("risk-check").contains("goods-check");
    }

    @Test
    @DisplayName("两个步骤 bean 重名 → IllegalStateException（「配置写的是哪一个」必须有唯一答案）")
    void duplicateStepNameRejected() {
        List<String> calls = new ArrayList<>();
        List<OrderCreateStep> beans = List.of(
                RecordingStep.ok("goods-check", calls), RecordingStep.ok("goods-check", calls));

        assertThatThrownBy(() -> new OrderCreatePipeline(beans, props("goods-check")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("步骤配置为空 → IllegalStateException（空流水线的订单永远 seal 不了，是「不报错但写坏数据」）")
    void emptyStepsRejected() {
        List<String> calls = new ArrayList<>();
        List<OrderCreateStep> beans = List.of(RecordingStep.ok("a", calls));

        assertThatThrownBy(() -> new OrderCreatePipeline(beans, props()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    @DisplayName("步骤 bean 的 name() 为空 / 重复的 bean 列表本身也被拦（配置不是唯一的错误来源）")
    void blankNameRejected() {
        List<String> calls = new ArrayList<>();

        assertThatThrownBy(() -> new OrderCreatePipeline(List.of(RecordingStep.ok("  ", calls)), props("a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能为空");
    }

    // ── 执行期：一步失败即中断 ──────────────────────────────────────────────────

    @Test
    @DisplayName("某步骤抛错 → 后续步骤不再执行，且异常原样抛出（原类型 / 原消息）")
    void failureBreaksTheChain() {
        List<String> calls = new ArrayList<>();
        List<OrderCreateStep> beans = List.of(
                RecordingStep.ok("a", calls), RecordingStep.failing("b", calls), RecordingStep.ok("c", calls));

        OrderCreatePipeline pipeline = new OrderCreatePipeline(beans, props("a", "b", "c"));
        Throwable thrown = catchThrowable(() -> pipeline.execute(anyOrder()));

        assertThat(thrown).isInstanceOf(ServiceException.class);
        assertThat(thrown.getMessage()).isEqualTo("步骤 b 失败");
        assertThat(calls).containsExactly("a", "b");
    }

    @Test
    @DisplayName("订单为 null → NPE（装配/调用错误，不是业务错误）")
    void nullOrderRejected() {
        List<String> calls = new ArrayList<>();
        OrderCreatePipeline pipeline = new OrderCreatePipeline(List.of(RecordingStep.ok("a", calls)), props("a"));

        assertThatThrownBy(() -> pipeline.execute(null)).isInstanceOf(NullPointerException.class);
        assertThat(calls).isEmpty();
    }
}
