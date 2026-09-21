package com.panoramic.trade.order.domain.port;

/**
 * 库存端口：扣减 + 按单回补（裁定 D2：<b>扣 {@code stock}</b>，{@code locked_stock} 已废弃、不参与任何口径）。
 *
 * <p>⚠ {@link #deduct} 必须是**原子**的「判断够不够 → 扣 → 写一条出库流水」三合一：拆成三个方法
 * （先 {@link #available}、再扣、再记录）会给并发留下「判断与扣减之间被插队」的窗口，超卖就从这里进来。
 * 本期内存适配器用同步块保证原子性，与将来真实实现的口径对齐——真实语义是
 * {@code UPDATE ... SET stock = stock - ? WHERE sku_id = ? AND stock >= ?} 看受影响行数，
 * 并在**同一事务**里插出库流水。</p>
 *
 * <p>⚠ <b>回补按「订单」而不是按「行」，且幂等由实现侧保证</b>（这一版相对上一版的修正）：
 * 上一版是 {@code revert(skuId, quantity, orderNo)} 逐行还，调用方要自己先读出库流水的**净额**、
 * 判断这一行到底扣过没有、扣了多少——于是「回补的幂等」这件事被摊到了编排层，
 * 而编排层根本不知道库存实现是怎样记账的。本版把它收进实现侧：</p>
 * <ul>
 *   <li>{@link #revertByOrder} 只收一个订单号，实现按自己的流水算出**该单每个 SKU 的净额**并归还；</li>
 *   <li>净额 {@code <= 0} 的行自然跳过 → 「失败发生在扣减之前」（没扣过）与「已经还过了」用同一套算式覆盖；</li>
 *   <li>重复调用是 **no-op**，故编排层不需要任何「我是不是回补过」的记忆。</li>
 * </ul>
 *
 * <p>⚠ 上一版那条「端口层不认重复回补」的取舍在这里被**推翻**，理由变了：那时去重键是
 * {@code orderNo + skuId}，而失败提交从不落库 → 同一秒里的两次失败提交可以拿到同一个单号，
 * 按它去重会把第二次**真实**回补静默吃掉。现在判据不再是「我是不是调过」而是
 * 「流水净额还剩多少」——净额是记账事实，不是调用痕迹，同一个单号被复用两次也不会算错。</p>
 *
 * <p>⚠ 本端口**不表态订单是否成立**：扣减只是「占住」，订单落库失败时由编排层调
 * {@link #revertByOrder} 归还（裁定 D13：库存写入与订单写入一次性提交，失败不留残单）。</p>
 */
public interface StockPort {

    /**
     * 原子扣减：库存足够才扣，扣成功即写一条**正**的出库流水
     *
     * @param skuId    店铺 SKU id
     * @param quantity 出库数量，必须为正
     * @param orderNo  所属订单号（写进流水，供 {@link #revertByOrder} 按单回补）
     * @return {@code true} = 扣减成功；{@code false} = 库存不足（**未扣、未记录**，什么都没发生）
     */
    boolean deduct(Long skuId, int quantity, String orderNo);

    /**
     * 按订单回补：把该单名下每个 SKU 的**未还净额**归还，并各追加一条**负**流水
     *
     * <p>⚠ 幂等：已经把该单归还干净的（净额归零）再调一次是 no-op，不会重复加库存。</p>
     *
     * <p>⚠ 回补不校验库存上限——回补是把之前扣掉的还回去，不是入库，不该被「仓库满了」之类的新规则挡住。</p>
     *
     * @param orderNo 订单号（回补的范围就是「这个单号名下还欠着的扣减」）
     */
    void revertByOrder(String orderNo);

    /**
     * 当前可用库存
     *
     * @param skuId 店铺 SKU id
     * @return 可用数量；未登记过的 SKU 视为 0
     */
    int available(Long skuId);
}
