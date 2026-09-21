package com.panoramic.trade.order.infrastructure.inmemory;

import com.panoramic.trade.order.domain.port.StockOutboundRecord;
import com.panoramic.trade.order.domain.port.StockPort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link StockPort} 的内存实现（本期无真实库存写入，裁定 D1/D2）。
 *
 * <p>⚠ <b>为什么所有写操作都在同步块里</b>：接口要求「判断够不够 → 扣 → 写出库记录」是**一次**原子操作
 * （拆开就有超卖窗口）。内存实现里这个原子的边界就是一把锁；将来换真实实现时，
 * 边界变成数据库的条件更新 + 同事务插记录（{@code UPDATE ... WHERE stock >= ?} 的影响行数），
 * **语义完全一致**，只是换了承载者。本类刻意用一把锁而不是分段锁/并发容器：
 * 它的职责是「语义对齐」，不是性能——性能问题在真实实现里由数据库解决。</p>
 *
 * <p>⚠ 出库记录**只追加**（正数出库、负数回补），回补不是删掉原记录：
 * 「净出库量」于是就是一次求和，断言「回滚后净出库为 0」不需要看任何操作类型字段。</p>
 *
 * <p>⚠ <b>{@link #revert} 刻意<b>不做</b>去重</b>（曾经按 {@code orderNo + skuId} 记一个「已回补」集合，
 * 已删）：那个去重键**不足以识别一次回补**。失败提交从不落库 → {@code existsByOrderNo} 对它恒 false →
 * 同一秒里的两次失败提交完全可能拿到同一个单号（生产上是随机序列 1/10000，而重试/补偿场景下
 * 就是同一次意图被重放）；若含同一 skuId，第二次回补会被当成「重复调用」静默吃掉：
 * 库存永久少扣、而出库流水净额却显示 0——账实不符且不报任何错。
 * 回补的幂等自此**由调用方保证**（见 {@code StockPort#revert} 的契约）：编排层按流水净额决定要不要还，
 * 同一个失败 episode 里每个 {@code (orderNo, skuId)} 至多还一次。</p>
 *
 * <p>⚠ <b>时钟注入而非 {@code LocalDateTime.now()}</b>：记录里的发生时刻一旦取自系统时间，
 * 单测就只能断言「有个时间」而不能断言「哪个时间」——时间相关的不变量（顺序、窗口）就没法钉住。</p>
 */
public class InMemoryStockPort implements StockPort {

    /** skuId → 可用库存（未登记过的 SKU 视为 0） */
    private final Map<Long, Integer> stock = new HashMap<>();

    /** 出库 / 回补流水（只追加） */
    private final List<StockOutboundRecord> records = new ArrayList<>();

    private final Clock clock;

    public InMemoryStockPort(Clock clock) {
        this.clock = clock;
    }

    /**
     * 预置库存（测试入口）
     *
     * @param skuId 店铺 SKU id
     * @param quantity 可用数量（不得为负：负库存在真实实现里不可能出现）
     */
    public synchronized void setStock(Long skuId, int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("预置库存不能为负数：" + quantity);
        }
        stock.put(skuId, quantity);
    }

    @Override
    public boolean deduct(Long skuId, int quantity, String orderNo) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("出库数量必须为正：" + quantity);
        }
        synchronized (this) {
            int current = stock.getOrDefault(skuId, 0);
            if (current < quantity) {
                // 不够就什么都不做：既不扣、也不留记录（与真实实现的「影响行数 0」同语义）
                return false;
            }
            stock.put(skuId, current - quantity);
            records.add(new StockOutboundRecord(skuId, quantity, orderNo, LocalDateTime.now(clock)));
            return true;
        }
    }

    @Override
    public void revert(Long skuId, int quantity, String orderNo) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("回补数量必须为正：" + quantity);
        }
        synchronized (this) {
            stock.merge(skuId, quantity, Integer::sum);
            records.add(new StockOutboundRecord(skuId, -quantity, orderNo, LocalDateTime.now(clock)));
        }
    }

    @Override
    public synchronized int available(Long skuId) {
        return stock.getOrDefault(skuId, 0);
    }

    @Override
    public synchronized List<StockOutboundRecord> outboundRecords() {
        return List.copyOf(records);
    }
}
