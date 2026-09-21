package com.panoramic.trade.order.domain.port;

/**
 * 「占幂等键」的结果：本次提交是**第一个**（{@code created=true}），还是**撞上了**一个已提交的同一请求。
 *
 * <p>⚠ <b>为什么用返回值而不是「重复键异常」表达这场竞争</b>：键被占用**不是错误**，是幂等命中——
 * 正是要挡的那类重复提交。用异常表达就得让编排层 catch 一个「本该发生」的分支，还得再查一次键
 * 才能拿到对方的 {@code submissionId}（两个来回）；用一个两字段的返回值，实现侧在一处
 * 把「插入失败 → 回查键」做完，调用方只看一个布尔。</p>
 *
 * <p>⚠ <b>并发语义由实现侧保证</b>：插入 {@code (customer_id, request_id)} 唯一键时，后到者会**阻塞**
 * 在唯一索引的行锁上，直到先到者提交或回滚——提交则后到者拿到重复键并回读到**先到者那一批**，
 * 回滚则后到者插入成功、成为新的 {@code created=true}。前者业务失败**不留残键**这件事就由
 * 「键与订单在同一事务里」自动达成，不需要任何清理动作（见 {@link OrderRepository#occupy}）。</p>
 *
 * @param submissionId 提交记录的 id（{@code created=true} 时是刚占下的，{@code false} 时是先到者的）
 * @param created      {@code true} = 本次占下了键，可以继续下单；{@code false} = 键已被占用，
 *                     调用方应当 {@link OrderRepository#findBySubmissionId} 回读整批并原样返回
 */
public record OccupyResult(long submissionId, boolean created) {
}
