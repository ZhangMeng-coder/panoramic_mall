package com.panoramic.trade.order.domain.port;

import java.util.List;

/**
 * 库存端口：扣减 + 回补 + 出库记录（裁定 D2：<b>扣 {@code stock}</b>，{@code locked_stock} 已废弃、不参与任何口径）。
 *
 * <p>⚠ {@link #deduct} 必须是**原子**的「判断够不够 → 扣 → 写出库记录」三合一：拆成三个方法（先 available、
 * 再扣、再记录）会给并发留下「判断与扣减之间被插队」的窗口，超卖就从这里进来。本期内存适配器用同步块
 * 保证原子性，与将来真实实现的口径对齐——真实语义是
 * {@code UPDATE ... WHERE stock >= ?} 看受影响行数，并在**同一事务**里插出库记录。</p>
 *
 * <p>⚠ {@link #revert} 必须按 {@code orderNo + skuId} **去重**：回滚路径本身可能被重试（或异常在多层被
 * 补回一次），重复回补会把库存越冲越多——那是比少扣更难发现的错误，因为它不会让任何一次下单失败。</p>
 *
 * <p>⚠ 本端口**不表态订单是否成立**：扣减只是「占住」，订单落库失败时由编排层调 {@link #revert} 归还
 * （裁定 D13：库存写入与订单写入一次性提交，失败不留残单）。</p>
 */
public interface StockPort {

    /**
     * 原子扣减：库存足够才扣，扣成功即写一条**正**的出库记录
     *
     * @param skuId    店铺 SKU id
     * @param quantity 出库数量，必须为正
     * @param orderNo  所属订单号（写进出库记录，也是回补去重的键）
     * @return {@code true} = 扣减成功；{@code false} = 库存不足（**未扣、未记录**，什么都没发生）
     */
    boolean deduct(Long skuId, int quantity, String orderNo);

    /**
     * 回补：追加一条**负**的出库记录并把数量加回库存
     *
     * <p>⚠ 同 {@code orderNo + skuId} **不重复回补**（幂等），且回补不校验库存上限——回补是把
     * 之前扣掉的还回去，不是入库，不该被「仓库满了」之类的新规则挡住。</p>
     *
     * @param skuId    店铺 SKU id
     * @param quantity 回补数量，必须为正（记录里会被写成负数）
     * @param orderNo  所属订单号
     */
    void revert(Long skuId, int quantity, String orderNo);

    /**
     * 当前可用库存
     *
     * @param skuId 店铺 SKU id
     * @return 可用数量；未登记过的 SKU 视为 0
     */
    int available(Long skuId);

    /**
     * 全部出库记录（**只追加的流水**，供断言「净出库」与排查使用）
     *
     * @return 按发生顺序排列的记录副本
     */
    List<StockOutboundRecord> outboundRecords();
}
