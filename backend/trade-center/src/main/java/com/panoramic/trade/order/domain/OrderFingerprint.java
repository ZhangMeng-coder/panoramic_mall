package com.panoramic.trade.order.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单指纹：识别「同一顾客在同一店铺、按同一来源、买同一批商品」的重复提交（裁定 D6 第二级幂等）。
 *
 * <p>算法：{@code sha256(customerId|source|storeId|排序后的 skuId:qty)} 取前 16 位 hex。</p>
 *
 * <p>为什么不做成整串拼接直接当键：{@code skuId:qty} 拼接串会很长（几十行就是几百字符），
 * 而指纹要进唯一索引 / 缓存键；取 16 位 hex（64 bit）在「同一窗口内的重复提交」这个量级上
 * 碰撞概率可忽略，同时长度可控。<b>它不是安全哈希</b>，不需要抗碰撞攻击——它的对手是手抖的顾客，不是攻击者。</p>
 *
 * <p>⚠ 敏感维度是四个，少一个都会把「两次不同的真实提交」误判成重复：</p>
 * <ul>
 *   <li>{@code customerId} —— 否则不同顾客的同一批商品会互相顶掉；</li>
 *   <li>{@code source} —— 详情页直购与购物车结算是两次意图不同的提交（见 {@link OrderSource}）；</li>
 *   <li>{@code storeId} —— 一单一店（裁定 D3），指纹也必须一店一个，否则多店拆单会互相判重；</li>
 *   <li>{@code skuId:qty} 有序集合 —— 对**行顺序**不敏感（购物车的选中行顺序可变），
 *       但对**数量**敏感（买 1 件与买 2 件是两笔单）。</li>
 * </ul>
 *
 * <p>⚠ 指纹**刻意不含**金额与时间：金额由服务端算（顾客改不了），时间每次都不同——含进去等于取消了幂等。</p>
 *
 * <p>⚠ 时间窗口（{@code idempotency-window-seconds}）不在这里，而在仓库查询的 {@code since} 参数上：
 * 指纹本身是纯函数，窗口是业务口径，两者分开才好各自调整。</p>
 */
public final class OrderFingerprint {

    /** 指纹长度：sha256 十六进制串的前 16 位 */
    private static final int FINGERPRINT_LENGTH = 16;

    private static final String ALGORITHM = "SHA-256";

    private OrderFingerprint() {
    }

    /**
     * 计算订单指纹
     *
     * @param customerId 顾客 id
     * @param source     订单来源
     * @param storeId    店铺 id（**拆单后的**那一笔的店铺）
     * @param lines      该笔订单的商品行（顺序不敏感）
     * @return 16 位小写 hex 指纹
     */
    public static String of(Long customerId, OrderSource source, Long storeId, List<OrderLine> lines) {
        Objects.requireNonNull(customerId, "顾客 id 不能为空");
        Objects.requireNonNull(source, "订单来源不能为空");
        Objects.requireNonNull(storeId, "店铺 id 不能为空");
        Objects.requireNonNull(lines, "订单行不能为空");

        // 先按 skuId 排序再拼：入参行顺序不同（购物车选中顺序、拆单后集合的遍历顺序）必须得到同一个指纹
        String linePart = lines.stream()
                .map(line -> Objects.requireNonNull(line, "订单行不能为空"))
                .sorted(Comparator.comparing(OrderLine::skuId))
                .map(line -> line.skuId() + ":" + line.quantity())
                .collect(Collectors.joining(","));

        String canonical = customerId + "|" + source.name() + "|" + storeId + "|" + linePart;
        return sha256Hex(canonical).substring(0, FINGERPRINT_LENGTH);
    }

    /**
     * 算 sha256 并转小写 hex
     *
     * @throws IllegalStateException JVM 不支持 SHA-256（不可能发生，属于环境被破坏，不是业务错误）
     */
    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 " + ALGORITHM + "，无法计算订单指纹", e);
        }
    }
}
