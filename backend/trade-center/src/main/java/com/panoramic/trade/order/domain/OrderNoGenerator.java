package com.panoramic.trade.order.domain;

/**
 * 订单号生成器（裁定 D5：业务可读单号 {@code yyyyMMddHHmmss} + 4 位序列）。
 *
 * <p>⚠ 它是**端口**（接口在 domain、实现在 infrastructure）而不是一个静态工具类，唯一目的是让
 * 「时钟」与「序列源」可注入：单测注入固定 {@code Clock} 与固定序列后，单号完全可预测，
 * 于是「生成 → 查重 → 重试 → 超限抛错」这条链路才有办法在纯 JUnit 里断言，而不必等真实时间流逝。</p>
 *
 * <p>⚠ 本接口**不负责查重**：重试与 {@code existsByOrderNo} 的配合在编排层（application）完成，
 * 因为「最多试几次」是业务口径（配置 {@code panoramic.trade.order.order-no-max-retry}），
 * 不是生成算法的一部分。</p>
 */
public interface OrderNoGenerator {

    /**
     * 生成一个新单号
     *
     * @return 业务可读单号，长度固定、可直接展示给用户
     */
    String next();
}
