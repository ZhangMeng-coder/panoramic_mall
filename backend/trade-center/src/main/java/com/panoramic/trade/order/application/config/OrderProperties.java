package com.panoramic.trade.order.application.config;

import com.panoramic.trade.order.domain.OrderStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单生成本期的三项可配口径（{@code panoramic.trade.order.*}）：
 * <b>跑哪些步骤、按什么顺序</b>（{@code steps}）、<b>状态怎么流转</b>（{@code status-flow}）、
 * 以及两个数量门槛（幂等窗口、单号重试上限）。
 *
 * <p>⚠ 为什么「步骤」与「状态顺序」必须可配（todo 原话：「通过配置文件或数据库的方式去配置订单生成步骤」）：
 * 这两件事都是**会变的业务口径**而不是算法——今天要在扣库存前加一步「风控校验」，明天要加一个「已退款」状态，
 * 都不该改流程代码。落法就是这里一个有序列表 + 步骤 bean 一个 {@code name()}，
 * 代码里没有任何 {@code if (step == ...)} 的分支。</p>
 *
 * <p>⚠ <b>本类只承载配置，不做校验</b>——校验分给两个真正的消费者，各自 fail fast：
 * {@code OrderStatusFlow} 断言状态配置覆盖枚举全部常量，{@code OrderCreatePipeline} 断言每个步骤名都找得到 bean。
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

    /** 订单状态流转顺序（只允许「下标 +1」的推进；必须覆盖 {@link OrderStatus} 全部常量） */
    private List<OrderStatus> statusFlow = new ArrayList<>();

    /** 幂等窗口（秒）：同一指纹在该窗口内视为重复提交，窗口外的同指纹是**新单** */
    private long idempotencyWindowSeconds = 300;

    /** 单号冲突时的重试上限：生成 → 查重 → 重试，连续这么多次都撞车即抛错（不无限重试） */
    private int orderNoMaxRetry = 5;
}
