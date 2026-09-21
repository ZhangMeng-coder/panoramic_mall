package com.panoramic.trade.order.infrastructure.inmemory;

import java.time.LocalDateTime;

/**
 * SKU 粒度的库存出库 / 回补记录（todo 原话：「根据 SKU 的粒度创建出库记录」）。
 *
 * <p>⚠ <b>落在 inmemory 包，不在 {@code domain.port}</b>（2026-09-21 落库期迁移）：{@code StockPort}
 * 已经不再暴露 {@code outboundRecords()}（回补改成按单净额、由实现自己记账），于是这个类型不再是
 * 端口契约的一部分，而是**内存脚手架自己的账本条目**——真实实现（store 域的库存流水表）不需要它，
 * 它就不该占着 domain 的一个位置。</p>
 *
 * <p>⚠ 它随 {@link InMemoryStockPort} 一起在 store 域落地后删除（todo 残留 9）。</p>
 *
 * <p>⚠ <b>一条记录同时承担出库与回补</b>，靠符号区分：{@code quantity} 为正 = 出库（订单占用），
 * 为负 = 回补（下单失败回滚）。这样「净出库量」就是简单的求和，不必再看操作类型字段，
 * 断言「回滚后净出库为 0」也就成了一行求和（P2 的回滚用例正是这么断言的）。</p>
 *
 * <p>⚠ 记录**只追加、不修改**：库存被扣过就必然留痕，回补是再写一条负记录而不是删掉原记录。
 * 出库记录是「库存为什么是现在这个数」的唯一解释，抹掉它等于抹掉审计。</p>
 *
 * @param skuId      店铺 SKU id
 * @param quantity   正数 = 出库，负数 = 回补
 * @param orderNo    所属订单号（回补时按它汇总净额：同一单同一 SKU 的正负相加为 0 即已还清）
 * @param occurredAt 发生时刻
 */
public record StockOutboundRecord(Long skuId, int quantity, String orderNo, LocalDateTime occurredAt) {
}
