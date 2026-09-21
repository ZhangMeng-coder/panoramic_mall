package com.panoramic.trade.order.infrastructure.inmemory;

import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.OrderSubmission;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link OrderRepository} 的内存实现（本期不落库，裁定 D1）。
 *
 * <p>⚠ {@link #saveSubmission} 在同步块里**一次性**写入订单与幂等映射：对应 D13 的口径——库存扣减与
 * 订单写入不是逐笔提交，要么都在、要么都不在（失败路径下这个方法根本不会被调用，故「失败不留残单」
 * 是免费的）。真实实现里这个「一次」是一次事务（订单行 + 提交记录同事务提交）。</p>
 *
 * <p>⚠ 写入时**按单号幂等**（已存在即忽略）：一次提交的整批里可能含复用笔（上一次提交留下的订单），
 * 它们已经在这个列表里了，再插一遍就会把订单条数、指纹判重、单号查重全部搅乱。</p>
 *
 * <p>⚠ <b>只保证单线程安全，并发下有计划内的缺口</b>：每个方法各自同步，但
 * 「{@link #findSubmission} → 下单扣库存 → {@link #saveSubmission}」这段跨方法的过程**不是原子的**。
 * 两个线程同时提交同一个 {@code customerId + requestId} 时会各下各的单，而 {@code putIfAbsent} 只留下首批，
 * 另一批订单就成了「一级幂等永远取不到的孤儿单」（库存已扣、重放却看不到它）。
 * 这是**有意不在这里修**的：修法是「占用幂等键先于执行业务」+ 键上的唯一约束，属**落库期**的设计，
 * 见 {@link OrderRepository#saveSubmission} 的接口注释。本实现是端口语义的占位，不是并发安全的参照实现。</p>
 *
 * <p>⚠ {@link #findByFingerprint} 的窗口过滤是**闭区间**（{@code createTime >= since}）：
 * 窗口的边界口径只有一处定义（在 {@code OrderRepository} 的接口注释里），本类只如实实现它，
 * 不在这里另立一套「到底含不含边界」的算法。</p>
 */
public class InMemoryOrderRepository implements OrderRepository {

    /** 已写入的订单（写入顺序 = 拆单顺序；不排序，读接口各自过滤） */
    private final List<OrderModel> orders = new ArrayList<>();

    /** 「顾客 + 请求 id」→ 那次提交返回的整批（第一级幂等的唯一凭证） */
    private final Map<String, OrderSubmission> submissions = new HashMap<>();

    @Override
    public synchronized void saveSubmission(OrderSubmission submission) {
        if (submission == null) {
            return;
        }
        // 先落订单、再落映射，且都在同一把锁里：不存在「订单落了而映射没落」的中间态可被读到
        for (OrderModel order : submission.orders()) {
            if (!existsByOrderNo(order.getOrderNo())) {
                orders.add(order);
            }
        }
        String requestId = submission.requestId();
        if (requestId != null && !requestId.isBlank()) {
            // putIfAbsent 而不是 put：同一键上「首次那批」才是唯一凭证，后到的提交不得覆盖它
            submissions.putIfAbsent(submissionKey(submission.customerId(), requestId), submission);
        }
    }

    @Override
    public synchronized Optional<OrderSubmission> findSubmission(Long customerId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(submissions.get(submissionKey(customerId, requestId)));
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

    /**
     * 幂等键：顾客 id 与 requestId 一起拼
     *
     * <p>⚠ 只用 requestId 会让「A 顾客的订单被 B 顾客用同一个键捞走」；{@code customerId} 为 null 时
     * 退化成 {@code null|xxx}——那是「没带顾客身份」的自成一格，仍不会跨顾客串到别人的订单上。</p>
     */
    private static String submissionKey(Long customerId, String requestId) {
        return customerId + "|" + requestId;
    }

    // ── 测试入口 ────────────────────────────────────────────────────────────────

    /**
     * 清空仓库（测试入口）
     *
     * <p>每个用例从一个空仓库开始，跨用例残留会让「仓库条数不变」这类断言失去意义。
     * 幂等映射与订单一并清掉：只清一半会让下一个用例意外命中上一个用例的提交记录。</p>
     */
    public synchronized void clear() {
        orders.clear();
        submissions.clear();
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
