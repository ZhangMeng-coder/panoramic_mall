package com.panoramic.store.rank;

import com.panoramic.store.config.StoreRankProperties;
import com.panoramic.store.service.StoreGoodsSpuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 排序分兜底重算：按固定节奏扫一批 {@code rank_dirty = 1} 的 SPU，逐个重算并清脏。
 *
 * <h3>为什么光有 {@code @Async} 不够</h3>
 * <p>需求要的是「最终结果对」，而异步事件<b>没有重试</b>：线程池打满、进程重启、重算抛异常，
 * 都会让那一次重算永久丢失——脏标记却还在（它存在库里，不随进程消失）。
 * 本任务就是「把这个标记扫干净」的那一层，也是「最终一致」真正的保证
 * （形态与 trade-center 超时关单任务同款：{@code fixedDelay} + 单批上限 + 失败不中断整批）。</p>
 *
 * <h3>与 listener 的分工</h3>
 * <p>{@link RankRecalcListener} 负责「快」（变更后立刻算，不阻塞业务），本类负责「不丢」
 * （每隔一段时间把剩下的脏行扫干净）。两者的算分入口是同一个
 * （{@code StoreGoodsSpuService#recalculateRank}），口径只有一份。</p>
 * <p>⚠ 本任务<b>不</b>把重算再丢回异步线程池：那是给「不想阻塞业务写路径」用的池，
 * 而本任务自己就跑在调度线程上、不阻塞任何请求路径。丢回去只会让池满时反过来阻塞调度线程。</p>
 *
 * <h3>幂等与并发</h3>
 * <p>任务本身幂等：算成的行不再满足 {@code rank_dirty = 1}，下一轮捞不到它。
 * 与 listener 撞同一行时，{@code rank_version} 守卫保证只会有一个把「算出的分 + 清脏」落库，
 * 另一个返回 {@code false}（算出的分已过期），脏标记由更晚的那次变更重新置 1，下一轮再算。</p>
 *
 * <h3>单笔失败分两类，不能混进一个桶</h3>
 * <p>{@code recalculateRank} 返回 {@code false}（算出的分已过期 / 商品已不存在）是<b>预期内</b>的
 * ——记 debug、不计入任何失败桶；{@code RuntimeException}（DB 不通、数据被写坏）是<b>故障</b>
 * ——带堆栈记 error，末尾汇总行也升到 error。混在一起时，「一轮 200 个全挂」与
 * 「一轮 200 个都被并发变更抢先」在日志上长得一模一样。⚠ 故障<b>不加重试</b>：脏标记没被清掉，
 * 下一轮扫描天然会重捞它。</p>
 *
 * <h3>两个可调项各自从哪来</h3>
 * <p>扫描节奏走 {@code @Scheduled} 的占位符（{@code panoramic.store.rank.sweep-interval-ms}，默认 30s）
 * ——注解只能吃一个字符串表达式，绕经配置类反而多一层转手；
 * 单批上限走 {@link StoreRankProperties}（它有装配期断言）。</p>
 * <p>⚠ {@code fixedDelay} 而不是 {@code fixedRate}：后者按「上一轮开始时刻」排下一次，
 * 一批算得久了会造成两轮扫描重叠（同一批脏行被两个线程同时重算），徒增无谓的竞争与日志噪音。</p>
 */
@Slf4j
@Component
public class RankRecalcTask {

    /**
     * 末尾汇总那一行（**唯一一份措辞**：两个级别共用一个格式串，故写成常量而不是两处字面量）。
     *
     * <p>⚠ 四个计数缺一不可：「重算 0 个」既可能是「没有脏行」（正常），也可能是
     * 「脏行全被并发变更抢先」（可疑）或「系统全挂」（故障）——不分桶就分不清该不该告警。</p>
     */
    private static final String SUMMARY =
            "排序分兜底重算：本轮捞到 {} 个脏 SPU，重算 {} 个，跳过 {} 个（算出的分已过期），失败 {} 个（系统错）";

    private final StoreGoodsSpuService spuService;

    /** 单批上限（来自配置，装配期断言为正数） */
    private final int batchSize;

    public RankRecalcTask(StoreGoodsSpuService spuService, StoreRankProperties properties) {
        this.spuService = spuService;
        this.batchSize = properties.getSweepBatchSize();
        // 上限必须为正：配成 0 或负数会让每一轮都捞不到任何脏行（任务看着在跑、其实一个都不算），
        // 且不报任何错。配置错是程序员错误，装配期就让它起不来
        if (batchSize <= 0) {
            throw new IllegalStateException("排序分兜底扫描的单批上限必须是正数（panoramic.store.rank.sweep-batch-size="
                    + batchSize + "），否则每一轮都捞不到脏行、任务静默失效");
        }
    }

    /**
     * 扫一轮：捞一批脏 SPU，逐个重算。
     *
     * <p>⚠ 上线时的「存量全量置脏」正是靠本任务收敛——届时第一轮就会捞满 {@code batchSize} 个，
     * 逐轮把积压清空（每轮之间隔一个 {@code sweep-interval-ms}）。</p>
     */
    @Scheduled(fixedDelayString = "${panoramic.store.rank.sweep-interval-ms:30000}")
    public void sweepDirtyRank() {
        List<Long> dirtyIds = spuService.listDirtyRankIds(batchSize);
        if (dirtyIds == null || dirtyIds.isEmpty()) {
            // 常态：绝大多数轮次无事可做（变更都被 listener 即时算掉了），故不打日志
            return;
        }
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        for (Long spuId : dirtyIds) {
            try {
                if (spuService.recalculateRank(spuId)) {
                    updated++;
                } else {
                    // 预期内：读完之后又有新变更（版本不符，脏标记已由那次变更重新置 1）或商品已不存在。
                    // 不打日志（计数在汇总行里），下一轮重捞
                    skipped++;
                }
            } catch (RuntimeException e) {
                // 系统错：脏标记没被清掉，下一轮扫描天然会重捞它，恢复后自动收敛——故不加重试
                failed++;
                log.error("排序分重算失败（系统错，下一轮会重捞）：spuId={}", spuId, e);
            }
        }
        // 记一行汇总：任务静默失效（一个都算不成）时，只有日志能把这件事说出来
        if (failed > 0) {
            log.error(SUMMARY, dirtyIds.size(), updated, skipped, failed);
        } else {
            log.info(SUMMARY, dirtyIds.size(), updated, skipped, failed);
        }
    }
}
