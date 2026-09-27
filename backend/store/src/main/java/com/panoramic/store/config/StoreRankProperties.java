package com.panoramic.store.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 排序分重算的运行参数（{@code panoramic.store.rank.*}）：异步重算线程池的
 * <b>并发度</b>与<b>排队上限</b>、兜底扫描的<b>单批上限</b>。
 *
 * <p>⚠ 这里<b>没有算分口径</b>（权重、{@code SALES_FULL}、{@code PRICE_FLOOR}、中性值）：
 * 那是业务口径，写在 {@code RankCalculator} 的常量与 store README 里。
 * 本类的三项都是<b>运维护栏</b>——它们只决定「算得多快 / 一批算几个」，
 * 改成任何值最终算出的分完全相同（故调大调小都不会让结果变样，只会让延迟变长变短）。</p>
 *
 * <p>⚠ 本类只承载配置，不做校验——校验分给两个真正的消费者，各自 fail fast：
 * {@code RankRecalcConfig} 构造线程池时断言 {@code poolSize > 0} 与 {@code queueCapacity > 0}，
 * {@code RankRecalcTask} 构造时断言 {@code sweepBatchSize > 0}。
 * 放在这里校验会得到一个「谁都能改的公共校验点」，反而看不出哪个配置项属于谁。</p>
 *
 * <p>⚠ 字段默认值只与 yml 里的初值保持一致（好让「yml 漏了某一项」不至于变成 0 或 null 的怪行为），
 * 它们<b>不是第二份口径来源</b>：非法值在装配期就会被上面的断言拦下、让服务起不来，
 * 而不是静默地把线程池配成 0（那会让 {@code @Async} 提交即抛 Rejected，重算只靠兜底扫描）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "panoramic.store.rank")
public class StoreRankProperties {

    /**
     * 重算线程池的常驻线程数（同时最多算几个 SPU）
     *
     * <p>⚠ 它<b>不</b>等于「算分能力上限」：算分是单行读 + 单行条件 UPDATE，
     * 极轻，2 个线程已足够跟上正常变更速率；配大只会让并发 UPDATE 争同一批行。</p>
     */
    private int poolSize = 2;

    /**
     * 重算线程池的排队上限（超出后由拒绝策略接手，见 {@code RankRecalcConfig}）
     *
     * <p>⚠ 队列<b>不能配成无界</b>：重算任务自带 spuId、几乎不占内存，但无界队列意味着
     * 「积压时提交永不失败」，进程重启前排在队里的任务全部丢失（脏标记仍在，兜底扫描会补），
     * 反而看不出积压。有界 + {@code CallerRunsPolicy} 让压力显式回落到提交线程。</p>
     */
    private int queueCapacity = 500;

    /**
     * 兜底扫描的单批上限（一轮最多重算几个脏 SPU）
     *
     * <p>⚠ 它是运维护栏而不是业务口径：脏行会积压（上线时全量置脏、任务停过一段），
     * 不限量就会让一轮的耗时与内存随积压量线性增长。先算这一批、下一轮再算剩下的，
     * 任务本身幂等（算成的行不再满足 {@code rank_dirty = 1}），故分批不会漏。</p>
     *
     * <p>⚠ 扫描<b>节奏</b>不在这里：它由 {@code RankRecalcTask} 的 {@code @Scheduled}
     * 直接读 {@code panoramic.store.rank.sweep-interval-ms}（注解只能吃一个字符串表达式），
     * 与 trade-center 超时关单任务同款。</p>
     */
    private int sweepBatchSize = 200;
}
