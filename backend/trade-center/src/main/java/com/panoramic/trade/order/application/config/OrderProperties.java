package com.panoramic.trade.order.application.config;

import com.panoramic.trade.order.domain.OrderStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单生成本期的可配口径（{@code panoramic.trade.order.*}）：
 * <b>跑哪些步骤、按什么顺序</b>（{@code steps}）、<b>状态主链怎么流转</b>（{@code status-flow}，
 * 两个结束过程走代码、不在这里）、三个数量门槛（幂等窗口、单号重试上限、支付时限）。
 *
 * <p>⚠ 为什么「步骤」与「状态顺序」必须可配（todo 原话：「通过配置文件或数据库的方式去配置订单生成步骤」）：
 * 这两件事都是**会变的业务口径**而不是算法——今天要在扣库存前加一步「风控校验」，明天要加一个「已退款」状态，
 * 都不该改流程代码。落法就是这里一个有序列表 + 步骤 bean 一个 {@code name()}，
 * 代码里没有任何 {@code if (step == ...)} 的分支。</p>
 *
 * <p>⚠ <b>本类只承载配置，不做校验</b>——校验分给两个真正的消费者，各自 fail fast：
 * {@code OrderStatusFlow} 断言「配置的主链 + 两个结束过程的落点」覆盖枚举全部常量，
 * {@code OrderCreatePipeline} 断言每个步骤名都找得到 bean。
 * 放在这里校验会得到一个「谁都能改的公共校验点」，反而看不出哪个配置项属于哪条链路。</p>
 *
 * <p>⚠ 字段默认值只与 yml 里的初值保持一致（好让「yml 漏了某一项」不至于变成 0 或 null 的怪行为），
 * 它们**不是第二份口径来源**：空白默认值（空 steps / 空 status-flow）在装配期就会被上面两条断言拦下，
 * 让服务起不来，而不是静默跑一条空流水线。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "panoramic.trade.order")
public class OrderProperties {

    /** 订单生成步骤名（数组顺序即执行顺序；名字对应 {@code OrderCreateStep#name()}） */
    private List<String> steps = new ArrayList<>();

    /**
     * 订单状态流转**主链**（**有序列表**：数组顺序即先后，相邻两项之间的边就是「推进一步」）
     *
     * <p>⚠ 形状是「线」不是「图」（2026-09-24 起，退回了 2026-09-23 那版的图口径）：两个结束过程
     * （待支付 → 已取消、已支付 → 已退款）不由配置表达——它们**就是那两个动作的定义**，
     * 写在 {@code OrderModel#markCancelled} / {@code OrderModel#markRefunded} 里。
     * 故**这两个状态名不许出现在本段 yml 里**：出现即装配失败（{@code OrderStatusFlow} 构造期拒绝）。</p>
     *
     * <p>必须列出 {@link OrderStatus} 里**除 {@link OrderStatus#CANCELLED} / {@link OrderStatus#REFUNDED}
     * 外**的全部常量、且首项必须是 {@link OrderStatus#PENDING_PAYMENT}（开单即待支付写在
     * {@code OrderModel#open} 里，配置说了不算），否则 {@code OrderStatusFlow} 装配失败、服务起不来。</p>
     *
     * <p>⚠ 列表**表达不了**「一个状态有多个来源」，那正是这一版要的：主链上每一步的下一步唯一，
     * 两个结束过程的落点不属于主链——写成本类型的形状就配不出图来。</p>
     *
     * <p>⚠ 这一项**没有上一版那种 yml 绑定形状的坑**：上一版的入口要写成空列表（{@code PENDING_PAYMENT: []}），
     * 得靠「空列表节点被摊平成空串再转成空集合」这条链才能绑定对；本版每一项都是实实在在的状态常量名。
     * 且绑定真的出了岔子也不是静默的——构造期那几条断言会让服务起不来。</p>
     */
    private List<OrderStatus> statusFlow = new ArrayList<>();

    /**
     * 幂等窗口（秒）：同一指纹在该窗口内视为重复提交，窗口外的同指纹是**新单**。
     *
     * <p>⚠ 窗口不是唯一条件：窗口内那一笔还得**仍未结束**（已收货 / 已取消 / 已退款不参与复用，
     * 见 {@code OrderRepository#findRecentByFingerprint}）。</p>
     */
    private long idempotencyWindowSeconds = 300;

    /** 单号冲突时的重试上限：生成 → 查重 → 重试，连续这么多次都撞车即抛错（不无限重试） */
    private int orderNoMaxRetry = 5;

    /**
     * 支付时限（分钟）：下单时按它算出**支付截止时刻**（{@code create_time + 本值}）落到
     * {@code trade_order.expire_time} 上，页面据此倒计时、支付时据此判「已过期」、
     * 超时关单任务据此捞超时未支付的单。
     *
     * <p>⚠ <b>它只在「下单那一刻」被读</b>：截止时刻是**算好落库的快照**，此后改这个值不会回头
     * 改动已下订单——与 {@code 地址快照 / storeName 快照} 同一口径（拿配置去重算老单，等于让
     * 「改一次配置」变成对存量订单的批量改写）。故读侧一律读库里的那一列，不重算。</p>
     *
     * <p>⚠ 必须为正数：{@code OrderCreateCoordinator} 构造时断言（≤0 会让截止时刻不晚于下单时刻，
     * 每一笔单刚开出来就是过期的）——此处只承载配置，不做校验（校验点归真正的消费者）。</p>
     */
    private int paymentTimeoutMinutes = 10;

    /**
     * 超时关单任务的**单批上限**（一次扫描最多关几笔）
     *
     * <p>⚠ 它是**运维护栏而不是业务口径**：超时未支付的单会积压（任务停过一段、故障后补跑），
     * 不限量就会让一次扫描的耗时与内存随积压量线性增长。先关这一批、下一轮再关剩下的，
     * 任务本身幂等（关过的单不再满足「仍停在待支付」），故分批不会漏。</p>
     *
     * <p>⚠ 必须为正数：{@code OrderTimeoutCloseTask} 构造时断言（0 或负数会让每一轮都捞不到单，
     * 任务看着在跑、其实一笔都不关）——此处只承载配置，不做校验（校验点归真正的消费者）。</p>
     *
     * <p>⚠ 扫描**节奏**不在这里：它由 {@code OrderTimeoutCloseTask} 的 {@code @Scheduled}
     * 直接读 {@code panoramic.trade.order.timeout-close-interval-ms}（注解只能吃一个字符串表达式）。</p>
     */
    private int timeoutCloseBatchSize = 200;
}
