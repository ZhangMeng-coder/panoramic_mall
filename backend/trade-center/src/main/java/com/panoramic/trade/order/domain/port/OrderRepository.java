package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderModel;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 订单仓库端口（本期只有内存实现；真实实现将是 {@code trade_order} / {@code trade_order_item} 的 MP 落库）。
 *
 * <p>⚠ 两条查询各自服务一条<b>幂等</b>支路，缺一条就会漏掉一种重复提交（裁定 D6 的两级幂等）：</p>
 * <ol>
 *   <li>{@link #findSubmission} —— 第一级：同一请求命中即**整批原样返回**（返回什么由提交记录说了算，
 *       而不是「按 requestId 查订单行」——理由见 {@link OrderSubmission}）；</li>
 *   <li>{@link #findByFingerprint} —— 第二级：拆单后的**每一笔**再按指纹在窗口内判重。</li>
 * </ol>
 *
 * <p>外加 {@link #existsByOrderNo} —— 单号查重，供「生成 → 查重 → 重试」使用。</p>
 *
 * <p>⚠ {@link #saveSubmission} 的语义是「**一次**写入这一次提交」，不是逐笔提交（裁定 D13）：
 * 步骤链全部成功、每笔都封存后才调用，失败则整批不落库、只剩「回补库存」一件事要做。</p>
 */
public interface OrderRepository {

    /**
     * 原子写入一次提交：订单与「{@code customerId + requestId} → 整批」映射一并落库
     *
     * <p>⚠ 两者必须在**同一个原子操作**里完成。若订单落了而映射没落，重放会退化到二级指纹复用
     * （窗口内各笔都能命中 → 仍返回同一批，故不会立刻出错），但**窗口外**的重放就没有任何东西能挡住它，
     * 会实实在在再下一单。映射不是缓存，是幂等凭证。</p>
     *
     * <p>⚠ 整批里可能含**复用笔**（此前提交留下、本次指纹命中的订单）：它们也要进这份记录
     * （重放要原样返回整批），但实现**不得重复插入**——按单号幂等（已存在即忽略）。
     * 这正是真实实现里「一批里既有已存在的行、也有新行」的必然形状，别试图靠调用方只传新建的笔来回避。</p>
     *
     * <p>⚠ {@code requestId} 为空则只写订单、不记映射（这次提交不做请求级去重）；
     * 同一 {@code customerId + requestId} 已有记录时**保留最先那条**——「首次那批」才是唯一凭证。</p>
     *
     * @param submission 待写入的提交记录（订单 + 幂等键）
     */
    void saveSubmission(OrderSubmission submission);

    /**
     * 按顾客 + 请求 id 查一次提交的整批订单（第一级幂等）
     *
     * @param customerId 下单顾客 id（⚠ **必须**参与收窄：幂等键的作用域是顾客内，跨顾客共享会外泄订单）
     * @param requestId  调用方带来的幂等键（可为 null / 空白：表示没传，直接未命中）
     * @return 首次那次提交返回的整批订单；没命中则空
     */
    Optional<OrderSubmission> findSubmission(Long customerId, String requestId);

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
