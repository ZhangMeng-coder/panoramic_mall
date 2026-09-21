package com.panoramic.trade.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单指纹：对**行顺序**不敏感、对**四个敏感维度**（顾客 / 来源 / 店铺 / 数量）敏感。
 *
 * <p>⚠ 这里钉死了一个绝对期望值（{@code fd9bd8bb659573ef}），它同时钉住了
 * 「拼接格式 + 排序规则 + 取前 16 位」三件事——指纹是要进唯一索引/缓存键的，
 * 悄悄改了格式就会让历史指纹全部失配（旧单再也判不出重复），必须由测试拦住。</p>
 */
class OrderFingerprintTest {

    private static final long CUSTOMER_ID = 11L;
    private static final long STORE_ID = 7L;

    private static String fingerprint(OrderSource source, Long storeId, List<OrderLine> lines) {
        return OrderFingerprint.of(CUSTOMER_ID, source, storeId, lines);
    }

    @Test
    @DisplayName("指纹格式固定：sha256 前 16 位、小写 hex、可重复计算")
    void fingerprintFormat() {
        String fingerprint = fingerprint(OrderSource.DIRECT, STORE_ID,
                List.of(new OrderLine(10L, 2), new OrderLine(20L, 1)));

        assertThat(fingerprint).isEqualTo("fd9bd8bb659573ef");
        assertThat(fingerprint).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 2), new OrderLine(20L, 1))))
                .as("同入参必然同指纹（纯函数）")
                .isEqualTo(fingerprint);
    }

    @Test
    @DisplayName("行顺序不同、集合相同 → 指纹相同（购物车选中顺序可变，不该被当成两次提交）")
    void lineOrderDoesNotMatter() {
        String ascending = fingerprint(OrderSource.CART, STORE_ID,
                List.of(new OrderLine(10L, 2), new OrderLine(20L, 1), new OrderLine(30L, 5)));
        String shuffled = fingerprint(OrderSource.CART, STORE_ID,
                List.of(new OrderLine(30L, 5), new OrderLine(10L, 2), new OrderLine(20L, 1)));

        assertThat(shuffled).isEqualTo(ascending);
    }

    @Test
    @DisplayName("顾客不同 → 指纹不同（否则顾客之间会互相顶掉对方的单）")
    void customerIdMatters() {
        String base = fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 1)));
        String other = OrderFingerprint.of(12L, OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 1)));

        assertThat(other).isNotEqualTo(base);
    }

    @Test
    @DisplayName("来源不同 → 指纹不同（详情页直购与购物车结算是两次意图不同的提交）")
    void sourceMatters() {
        String direct = fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 1)));
        String cart = fingerprint(OrderSource.CART, STORE_ID, List.of(new OrderLine(10L, 1)));

        assertThat(cart).isNotEqualTo(direct);
    }

    @Test
    @DisplayName("店铺不同 → 指纹不同（一单一店，拆单后各店各自判重）")
    void storeIdMatters() {
        String storeSeven = fingerprint(OrderSource.DIRECT, 7L, List.of(new OrderLine(10L, 1)));
        String storeEight = fingerprint(OrderSource.DIRECT, 8L, List.of(new OrderLine(10L, 1)));

        assertThat(storeEight).isNotEqualTo(storeSeven);
    }

    @Test
    @DisplayName("数量不同 → 指纹不同（买 1 件与买 2 件是两笔单）")
    void quantityMatters() {
        String one = fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 1)));
        String two = fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 2)));

        assertThat(two).isNotEqualTo(one);
    }

    @Test
    @DisplayName("SKU 集合不同 → 指纹不同")
    void skuSetMatters() {
        String withTen = fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(10L, 1)));
        String withTwenty = fingerprint(OrderSource.DIRECT, STORE_ID, List.of(new OrderLine(20L, 1)));

        assertThat(withTwenty).isNotEqualTo(withTen);
    }

    @Test
    @DisplayName("空行列表也算得出指纹（不去重、只如实反映入参）")
    void emptyLinesStillProduceFingerprint() {
        assertThat(fingerprint(OrderSource.DIRECT, STORE_ID, List.of())).matches("[0-9a-f]{16}");
    }

    @Test
    @DisplayName("敏感维度为 null → NPE（缺少维度就算不出可信指纹，不能静默降级）")
    void nullDimensionsRejected() {
        assertThatThrownBy(() -> OrderFingerprint.of(null, OrderSource.DIRECT, STORE_ID, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OrderFingerprint.of(CUSTOMER_ID, null, STORE_ID, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OrderFingerprint.of(CUSTOMER_ID, OrderSource.DIRECT, null, List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OrderFingerprint.of(CUSTOMER_ID, OrderSource.DIRECT, STORE_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
