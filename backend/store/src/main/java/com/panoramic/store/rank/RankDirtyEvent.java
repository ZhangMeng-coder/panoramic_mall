package com.panoramic.store.rank;

/**
 * 「该 SPU 的排序分需要重算」的域内事件（进程内 Spring 事件，<b>不</b>走 MQ）。
 *
 * <h3>为什么是事件而不是直接调重算</h3>
 * <p>置脏发生在<b>业务事务之内</b>（价格变 / 评分变 / 销量变的写路径上）。此刻直接重算会
 * <b>读到未提交的旧值</b>（如刚写入的评价还没提交，重算读到的还是旧评分），并把这个过期结果
 * 连同「已清脏」一起落库 → 该商品此后<b>永远</b>停在旧分上。故置脏与重算之间必须隔着事务边界：
 * 置脏侧只发事件，重算侧由 {@link RankRecalcListener} 以 {@code AFTER_COMMIT} 接收。</p>
 *
 * <h3>为什么事件而不是服务间调用</h3>
 * <p>算分（SPU 服务）与触发（评价服务 / 库存联动 / 销量入账）都在 store 域内，
 * 事件让依赖保持单向（触发方 → 事件 → ①算分），不会造出「评价服务 ↔ SPU 服务」的循环。</p>
 *
 * <p>⚠ 事件的投递<b>不可靠</b>（异步、无重试、进程重启即丢），故它<b>不是</b>最终一致的保证——
 * 保证落在 {@code store_goods_spu.rank_dirty} 这个持久标记上，由 {@link RankRecalcTask} 兜底扫。
 * 本事件只负责「大多数情况下尽快算出来」。</p>
 *
 * @param spuId 需要重算的店铺商品 id
 */
public record RankDirtyEvent(Long spuId) {
}
