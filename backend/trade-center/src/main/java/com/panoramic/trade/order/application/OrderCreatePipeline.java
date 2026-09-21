package com.panoramic.trade.order.application;

import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 订单生成流水线：把「容器里有哪些步骤 bean」与「配置要跑哪些、什么顺序」对账成一条可执行的链（裁定 D9）。
 *
 * <p>⚠ <b>为什么把对账放在构造期而不是每次执行时</b>：配置与 bean 不匹配是**装配错误**——
 * 要么是有人把 yml 里的步骤名写错了，要么是删了一个步骤实现却忘了摘配置。这两种错都不该等到
 * 某位顾客下单时才炸（那时错误已经落在一笔真实交易上，且日志里看不出是配置问题）。
 * 构造期直接抛 {@link IllegalStateException}，服务就起不来，问题在启动日志里一眼可见。</p>
 *
 * <p>⚠ <b>为什么不按 bean 的发现顺序执行</b>：bean 的发现顺序（包扫描、依赖关系）不表达任何业务意图，
 * 而「先验商品再扣库存最后算钱」是有顺序的——顺序的唯一来源只能是配置。故这里刻意让
 * <b>配置顺序覆盖 bean 顺序</b>：容器给什么顺序都不影响结果，配置里删掉一个步骤它就真的不跑。</p>
 *
 * <p>⚠ 本类不是 Spring bean，由装配层用 {@code @Bean} 显式装配（入参是容器里全部 {@link OrderCreateStep}）：
 * 「哪几步」属于可插拔实现（各自 {@code @Component}），而「串成一条链」是装配职责，两者分开才好替换。</p>
 */
public class OrderCreatePipeline {

    /** 按配置顺序排好的步骤（本类是这条链的唯一持有者，对外只给名字） */
    private final List<OrderCreateStep> steps;

    /**
     * 按配置把步骤 bean 串成一条链
     *
     * @param availableSteps 容器里全部步骤 bean（顺序不参与决策，只有 {@code name()} 参与）
     * @param properties     步骤配置（{@code panoramic.trade.order.steps}，数组顺序即执行顺序）
     * @throws IllegalStateException 步骤配置为空、两个 bean 重名、配置里的步骤名重复、配置里出现容器中没有的步骤名
     */
    public OrderCreatePipeline(List<OrderCreateStep> availableSteps, OrderProperties properties) {
        Objects.requireNonNull(availableSteps, "步骤 bean 列表不能为空");
        Objects.requireNonNull(properties, "订单配置不能为空");
        List<String> configured = properties.getSteps() == null ? List.of() : properties.getSteps();
        if (configured.isEmpty()) {
            // 一条空的流水线什么都补不全：订单永远 seal 不了，却会被当成正常订单存下去——
            // 这是「不报错但写坏数据」，故在装配期就拦下
            throw new IllegalStateException("订单生成步骤配置不能为空（panoramic.trade.order.steps 至少要有一个步骤，否则订单永远无法封存）");
        }
        this.steps = resolve(availableSteps, configured);
    }

    /**
     * 按配置顺序执行整条链
     *
     * <p>⚠ 任一步骤抛错即**中断**，后续步骤不执行：半成品订单在 seal 时本来也过不了，
     * 继续跑只会多几次无意义的跨域查询。已发生的一半（如库存已扣）由编排层统一回补（裁定 D4）。</p>
     *
     * @param order 待处理的订单
     */
    public void execute(OrderModel order) {
        Objects.requireNonNull(order, "订单不能为空");
        for (OrderCreateStep step : steps) {
            step.execute(order);
        }
    }

    /**
     * @return 本次装配出的执行顺序（步骤名，**不可变副本**）——供装配层单测把「配置顺序」与「实际顺序」对上
     */
    public List<String> stepNames() {
        return steps.stream().map(OrderCreateStep::name).toList();
    }

    /**
     * 建「名字 → 步骤 bean」索引，再按配置顺序取出步骤
     *
     * <p>报错消息里同时给出「配置里的名字」与「实际可用的名字集合」：这两条信息分开看都无用——
     * 只看前者不知道能写什么，只看后者不知道错的是哪一行配置。</p>
     *
     * <p>⚠ <b>配置里的重复项必须拦</b>：{@code configured} 是**执行序列**而不是集合，
     * 配成 {@code [goods-check, stock-check, stock-check, price-compute]} 会让库存被扣两遍——
     * 跑得出结果、也不报错，只是每单静默多占一份库存。这与 {@code OrderStatusFlow} 拒绝重复状态
     * 是同一个口径（两侧不能一边严一边松），故在这里拦下，而不是指望「谁会配错呢」。</p>
     */
    private static List<OrderCreateStep> resolve(List<OrderCreateStep> availableSteps, List<String> configured) {
        Map<String, OrderCreateStep> byName = new LinkedHashMap<>();
        for (OrderCreateStep step : availableSteps) {
            Objects.requireNonNull(step, "步骤 bean 不能为空");
            String name = step.name();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("订单生成步骤的 name() 不能为空：" + step.getClass().getName());
            }
            // 重名不报错的话，「配置写的是哪一个」就没有答案了：同一个名字可能对应两个实现，跑哪个取决于容器顺序
            OrderCreateStep previous = byName.putIfAbsent(name, step);
            if (previous != null) {
                throw new IllegalStateException("订单生成步骤名重复：" + name
                        + "（" + previous.getClass().getName() + " 与 " + step.getClass().getName() + "）");
            }
        }
        List<OrderCreateStep> resolved = new ArrayList<>(configured.size());
        Set<String> used = new HashSet<>();
        for (String name : configured) {
            OrderCreateStep step = name == null ? null : byName.get(name);
            if (step == null) {
                throw new IllegalStateException("订单生成步骤配置里出现未知的步骤名：" + name
                        + "（实际可用的步骤名：" + byName.keySet() + "）");
            }
            if (!used.add(name)) {
                throw new IllegalStateException("订单生成步骤配置里出现重复的步骤名：" + name
                        + "（同一步骤会被执行多次，例如库存被扣两遍；请检查 panoramic.trade.order.steps）");
            }
            resolved.add(step);
        }
        return List.copyOf(resolved);
    }
}
