package com.panoramic.trade.order.infrastructure.inmemory;

import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.OrderRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link OrderRepository} 的内存实现（本期不落库，裁定 D1）。
 *
 * <p>⚠ {@link #saveAll} 在同步块里**一次性**写入整批：对应 D13 的口径——库存扣减与订单写入不是逐笔提交，
 * 要么这一批都在，要么都不在（失败路径下这个方法根本不会被调用，故「失败不留残单」是免费的）。
 * 顺序也刻意保留：列表按写入顺序存，{@code findByRequestId} 才能原样交回「上次那一批」。</p>
 *
 * <p>⚠ {@link #findByFingerprint} 的窗口过滤是**闭区间**（{@code createTime >= since}）：
 * 窗口的边界口径只有一处定义（在 {@code OrderRepository} 的接口注释里），本类只如实实现它，
 * 不在这里另立一套「到底含不含边界」的算法。</p>
 */
public class InMemoryOrderRepository implements OrderRepository {

    /** 已写入的订单（写入顺序 = 拆单顺序；不排序，读接口各自过滤） */
    private final List<OrderModel> orders = new ArrayList<>();

    @Override
    public synchronized void saveAll(List<OrderModel> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        this.orders.addAll(orders);
    }

    @Override
    public synchronized List<OrderModel> findByRequestId(String requestId) {
        if (requestId == null) {
            return List.of();
        }
        return List.copyOf(orders.stream().filter(order -> requestId.equals(order.getRequestId())).toList());
    }

    @Override
    public synchronized Optional<OrderModel> findByFingerprint(String fingerprint, LocalDateTime since) {
        if (fingerprint == null || since == null) {
            return Optional.empty();
        }
        return orders.stream()
                .filter(order -> fingerprint.equals(order.getFingerprint()))
                // 闭区间：窗口起点那一刻算「窗口内」（与仓库接口注释同口径）
                .filter(order -> !order.getCreateTime().isBefore(since))
                .findFirst();
    }

    @Override
    public synchronized boolean existsByOrderNo(String orderNo) {
        if (orderNo == null) {
            return false;
        }
        return orders.stream().anyMatch(order -> orderNo.equals(order.getOrderNo()));
    }

    // ── 测试入口 ────────────────────────────────────────────────────────────────

    /**
     * 清空仓库（测试入口）
     *
     * <p>每个用例从一个空仓库开始，跨用例残留会让「仓库条数不变」这类断言失去意义。</p>
     */
    public synchronized void clear() {
        orders.clear();
    }

    /**
     * @return 已写入的订单笔数（测试入口：断言「失败不留残单」「复用不重复落库」）
     */
    public synchronized int count() {
        return orders.size();
    }

    /**
     * @return 全部订单（不可变副本，写入顺序；测试入口：需要整体核对时用）
     */
    public synchronized List<OrderModel> all() {
        return List.copyOf(orders);
    }
}
