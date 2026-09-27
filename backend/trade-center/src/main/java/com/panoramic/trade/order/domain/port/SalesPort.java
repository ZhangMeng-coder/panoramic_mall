package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderModel;

/**
 * 销量端口：把一笔**已完成**订单的销量推给 store 域（销量是那边的事实，本域不存第二份）。
 *
 * <p>⚠ <b>没有返回值、也没有「推成功了吗」的语义</b>：销量是本域推出去的**下游推导量**，
 * 不是本域的事实。本域能承诺的只有「推过一次」，能不能落地由 store 域回答
 * ——失败时异常上抛（见下），成功与否不影响订单本身。</p>
 *
 * <h3>为什么这是全仓第二条跨域写边，且与库存端口同向不同命</h3>
 * <p>它与 {@link StockPort} 走同一条边（trade-center → store，全仓唯一的跨域调用方向，
 * 见 cross-cutting 第 24 条），但**失败处置完全相反**：</p>
 * <ul>
 *   <li>{@link StockPort#deduct} 失败 = 下单失败（下游不可达则整次下单回滚）——它是**前置条件**；</li>
 *   <li>本端口失败 = **只记一行日志**，收货照样成功——它是**事后通知**。
 *       理由见 {@code StoreClient#addSalesByOrder}：货已收到、钱已付，为了记一个销量把顾客的
 *       收货动作判成失败，是把「统计口径」凌驾于「业务事实」之上。这是第 24 条降级方向的
 *       **第二处例外**（第一处是库存回补：补偿路径宁可不做也不能炸）。</li>
 * </ul>
 * <p>⚠ <b>故「只 warn」不落在这里，落在调用方</b>（{@code OrderSalesPushListener} / {@code OrderSalesPushTask}）：
 * 本端口与它的实现**照常把异常上抛**，与 {@link StockPort} 同款——端口层替调用方决定「这个错要不要吞」
 * 就没人能一眼看出哪条边的失败会被吞掉了。</p>
 *
 * <h3>幂等由 store 域提供，本域因此可以放心重推</h3>
 * <p>store 域的台账唯一键是 {@code (order_no, spu_id)}，重复推送是 <b>no-op 成功</b>。
 * 本域据此设计成「**至少推一次**」：快速路径（收货提交后立即推）+ 兜底扫描（补推未标记的）
 * 允许撞同一笔单，撞了也不会错账。</p>
 * <p>⚠ <b>但要与之配套的是「推成功之后才标记」</b>：没有标记就只能重推，标记在推之前就等于
 * 承诺了一件没发生的事（丢一次 = 永久少记一笔，因为再也没有东西会重推它）。顺序反过来则最坏
 * 只是多推一次。</p>
 */
public interface SalesPort {

    /**
     * 推送一笔已完成订单的销量（各 SPU 的合计件数）
     *
     * <p>⚠ <b>只对「已完成」的订单有意义</b>：本端口不校验状态（域端口不做业务判定），
     * 调用方必须自己确认这笔单确实是 {@code RECEIVED}——推早了会把「还没完成的购买」记成销量。</p>
     *
     * <p>⚠ 订单明细里同一 SPU 可能有多行（同一商品的不同 SKU）：**合计成一行再推**，
     * 否则同一个 {@code (orderNo, spuId)} 会撞 store 的台账唯一键、整条推送失败
     * （口径在 {@code StoreGoodsSalesItemDTO}）。</p>
     *
     * @param order 已完成（{@code RECEIVED}）的订单聚合（取单号 + 明细）
     * @throws RuntimeException store 域不可达 / 熔断打开 / store 侧业务失败（由调用方决定吞还是报）
     * @throws IllegalStateException 订单没有任何明细（数据被写坏，没有任何东西可推）
     */
    void pushSales(OrderModel order);
}
