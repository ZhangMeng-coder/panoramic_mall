package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓库端口（本期只有内存实现；真实实现将是 {@code trade_order} / {@code trade_order_item} 的 MP 落库）。
 *
 * <p>⚠ 三个查询方法各自服务一条<b>幂等</b>支路，缺一条就会漏掉一种重复提交（裁定 D6 的两级幂等）：</p>
 * <ol>
 *   <li>{@link #findByRequestId} —— 第一级：同一请求 id 命中即整批原样返回；</li>
 *   <li>{@link #findByFingerprint} —— 第二级：拆单后的**每一笔**再按指纹在窗口内判重；</li>
 *   <li>{@link #existsByOrderNo} —— 单号查重，供「生成 → 查重 → 重试」使用。</li>
 * </ol>
 *
 * <p>⚠ {@link #saveAll} 的语义是「**一次**写入这一批订单」，不是逐笔提交（裁定 D13）：步骤链全部成功、
 * 状态已置待支付后才调用，失败则整批不落库、只剩「回补库存」一件事要做。</p>
 */
public interface OrderRepository {

    /**
     * 一次性写入一批订单（拆单后同一顾客的一批）
     *
     * @param orders 待写入的订单（调用方保证都是新建、尚未入库）
     */
    void saveAll(List<OrderModel> orders);

    /**
     * 按请求 id 查整批订单（第一级幂等）
     *
     * @param requestId 调用方带来的幂等键（可为 null：为空表示这次提交不做请求级去重）
     * @return 该请求已产生的全部订单；没有则空列表（**不是 null**）
     */
    List<OrderModel> findByRequestId(String requestId);

    /**
     * 按指纹在时间窗口内查一笔订单（第二级幂等）
     *
     * @param fingerprint 订单指纹（由 {@code OrderFingerprint} 算出，不含金额与时间）
     * @param since       窗口起点：只认 {@code createTime >= since} 的订单
     * @return 命中的那一笔；没有则空
     */
    Optional<OrderModel> findByFingerprint(String fingerprint, LocalDateTime since);

    /**
     * 单号是否已被占用（生成后查重 + 重试用）
     *
     * @param orderNo 业务可读单号
     * @return 已存在则为 {@code true}
     */
    boolean existsByOrderNo(String orderNo);
}
