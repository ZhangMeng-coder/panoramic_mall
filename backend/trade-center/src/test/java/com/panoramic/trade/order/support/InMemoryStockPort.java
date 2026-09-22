package com.panoramic.trade.order.support;

import com.panoramic.trade.order.domain.port.StockPort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link StockPort} 的**测试假实现**（行为层单测的夹具）。
 *
 * <p>⚠ 库存的真实归属是 store 域（真适配器见 `infrastructure/feign/StockAdapter`，扣减是那边的一条
 * 原子条件更新 + 同事务写流水）。本类随 T4b 从 `main` 搬到 `src/test/.../order/support/`，
 * 只服务「不连下游也要验库存语义」的那批用例——它的价值在于**语义对齐**（原子性、按单净额回补），
 * 不在性能；并发与真的超卖防护由 store 域的数据库承担。</p>
 *
 * <p>⚠ <b>为什么所有写操作都在同步块里</b>：接口要求「判断够不够 → 扣 → 记账」是**一次**原子操作
 * （拆开就有超卖窗口）。内存实现里这个原子的边界就是一把锁；将来换真实实现时，
 * 边界变成数据库的条件更新 + 同事务插流水（{@code UPDATE ... WHERE stock >= ?} 的影响行数），
 * **语义完全一致**，只是换了承载者。本类刻意用一把锁而不是分段锁/并发容器：
 * 它的职责是「语义对齐」，不是性能——性能问题在真实实现里由数据库解决。</p>
 *
 * <p>⚠ 流水**只追加**（正数出库、负数回补），回补不是删掉原记录：
 * 「净出库量」于是就是一次求和，断言「回滚后净出库为 0」不需要看任何操作类型字段。</p>
 *
 * <p>⚠ <b>回补按「单 + SKU 的净额」判，不按「调没调过」判</b>（2026-09-21 口径修正）：
 * 早先的做法是在端口契约里要求调用方保证「同一失败 episode 每个 {@code (orderNo, skuId)} 至多还一次」，
 * 并删掉了一个用 {@code orderNo + skuId} 记「已回补」的集合——因为那个去重键不足以识别一次回补
 * （失败提交从不落库 → 同一秒的两次失败提交可能拿到同一个单号，第二次回补会被当重复静默吃掉，
 * 库存永久少扣而流水净额显示 0，**账实不符且不报错**）。现在改成看**账本自己**：
 * 净额 &gt; 0 才有欠，还完净额归 0，重复调用自然成为 no-op。这个判据不需要调用方守信，
 * 也不依赖单号是否唯一——把「幂等」从事先约定变成了可观测事实。</p>
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
     * @param skuId    店铺 SKU id
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

    /**
     * 按单回补：把这笔单在账本上**还欠着**的每个 SKU 归还回去
     *
     * <p>欠多少由流水自己算：同一单同一 SKU 的正负相加就是净额——出库一次是 {@code +n}，
     * 归还一次追加 {@code -n}，净额归 0 后再调即无欠可还（{@link StockPort#revertByOrder} 要求的幂等
     * 就是这么得到的，不需要额外的「已回补」标记）。</p>
     *
     * <p>⚠ 逐 SKU 分组用 {@code LinkedHashMap}：归还顺序 = 该单首次出库的 SKU 顺序，
     * 与真实实现里「按流水顺序回补」一致，日志可读、测试可断言。</p>
     */
    @Override
    public void revertByOrder(String orderNo) {
        Objects.requireNonNull(orderNo, "回补的订单号不能为空");
        synchronized (this) {
            Map<Long, Integer> owed = new LinkedHashMap<>();
            for (StockOutboundRecord record : records) {
                if (orderNo.equals(record.orderNo())) {
                    owed.merge(record.skuId(), record.quantity(), Integer::sum);
                }
            }
            for (Map.Entry<Long, Integer> entry : owed.entrySet()) {
                int net = entry.getValue();
                if (net <= 0) {
                    continue;   // 没欠（净额 0 = 已还清；负数只可能来自人工预置库存，不归本方法管）
                }
                stock.merge(entry.getKey(), net, Integer::sum);
                records.add(new StockOutboundRecord(entry.getKey(), -net, orderNo, LocalDateTime.now(clock)));
            }
        }
    }

    @Override
    public synchronized int available(Long skuId) {
        return stock.getOrDefault(skuId, 0);
    }

    /**
     * @return 全部出库 / 回补流水（不可变副本、追加顺序；**测试入口**——它已不是端口的一部分）
     */
    public synchronized List<StockOutboundRecord> outboundRecords() {
        return List.copyOf(records);
    }
}
