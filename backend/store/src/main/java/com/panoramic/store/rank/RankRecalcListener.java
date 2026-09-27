package com.panoramic.store.rank;

import com.panoramic.store.service.StoreGoodsSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 排序分重算监听器：接 {@link RankDirtyEvent}，在<b>事务提交之后</b>、于<b>自定义线程池</b>上重算该 SPU。
 *
 * <h3>两个注解各解决一件事，缺一不可</h3>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}：<b>正确性</b>。置脏发生在业务事务内，
 *       此刻重算会读到未提交的旧值，并把过期结果连同「已清脏」一起落库 → 该商品永远停在旧分上
 *       （见 {@link RankDirtyEvent}）。⚠ {@code fallbackExecution = true} 是必需的：置脏点里有不带事务的
 *       写路径（如单条条件 UPDATE 自带的隐式事务），没有活跃事务时若静默跳过，这些变更就只剩兜底扫描。</li>
 *   <li>{@code @Async("rankRecalcExecutor")}：<b>不阻塞业务</b>（需求：「尽量不要影响前端查询商品列表和详情」）。
 *       收货推送 / 提交评价等写路径提交完立即返回，算分在后台线程池上跑。</li>
 * </ul>
 *
 * <h3>失败一律不外抛，只留脏标记</h3>
 * <p>本方法 catch 掉一切 {@code RuntimeException}：重算失败<b>不是</b>写路径的失败——业务事务早已提交，
 * 这里再把异常抛出去只会污染一个已经成功的请求（且 {@code @Async} 下它本来也回不到调用方）。
 * 失败后 {@code rank_dirty} 仍是 1（清脏与算分在一条 UPDATE 里，没算成就不清），
 * 由 {@link RankRecalcTask} 下一轮重捞。故「重算丢失」不是一个需要重试机制处理的问题。</p>
 *
 * <h3>同 SPU 去重（进程内）</h3>
 * <p>{@code inFlight} 键集避免同一商品在排队中被重复提交（如一次改价写两张 SKU，会连发两次事件）。
 * ⚠ 与 {@code rank_dirty} 的版本守卫配合后，这里的「跳过」是安全的：
 * 若被跳过的那次变更晚于在途重算读到的版本，在途的那次会因版本不符而<b>不清脏</b>，兜底扫描接手；
 * 若早于，则它已包含在在途重算里。故去重漏了或多了都不影响最终结果，只是少排一次队。</p>
 * <p>⚠ <b>只在本进程内去重</b>：store 域无 redis 依赖（pom 里没有）。多实例部署时会漏去重，
 * 但漏了只是「同一 SPU 排两次队、算出同一个分」，不影响正确性——不为它引 redis。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankRecalcListener {

    private final StoreGoodsSpuService spuService;

    /** 在途去重键集（进程内；见类注释的口径） */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 重算单个 SPU 的排序分（事务提交后、线程池上执行；失败只记日志）
     *
     * @param event 置脏事件（携带 spuId）
     */
    @Async("rankRecalcExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRankDirty(RankDirtyEvent event) {
        Long spuId = event.spuId();
        if (spuId == null) {
            return;
        }
        if (!inFlight.add(spuId)) {
            // 同一商品已在途：跳过（不是丢——脏标记还在，在途那次要么算出含本次变更的分、要么留下脏标记给兜底）
            return;
        }
        try {
            boolean updated = spuService.recalculateRank(spuId);
            if (!updated) {
                // 版本不符（读完之后又有新变更）或商品已不存在：**正常**情形，不打日志、不报错。
                // 脏标记仍在，兜底扫描下一轮重算
                log.debug("排序分重算跳过（算出的分已过期，留给兜底扫描）：spuId={}", spuId);
            }
        } catch (RuntimeException e) {
            // 系统错（DB 不通 / 数据被写坏）：带堆栈记 warn，**吞掉**——业务事务已提交，这里抛出只会污染请求。
            // 脏标记没被清掉，兜底扫描会重捞，故无需重试
            log.warn("排序分重算失败，留给兜底扫描：spuId={}", spuId, e);
        } finally {
            inFlight.remove(spuId);
        }
    }
}
