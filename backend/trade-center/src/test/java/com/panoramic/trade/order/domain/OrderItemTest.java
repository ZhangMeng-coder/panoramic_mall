package com.panoramic.trade.order.domain;

import com.panoramic.trade.order.domain.port.SkuSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单项：数量 / 单价边界、两段式补全的顺序、以及「对外不可变」（无 public setter、无 public 补全入口）。
 *
 * <p>⚠ 这里的边界用例是刻意的穷尽写法（0 / 1 / 999 / 1000、0.00 / 0.01）：
 * 数量与单价是**唯一两个来自用户输入的数值**，它们的边界就是超卖与负价的入口。</p>
 */
class OrderItemTest {

    private static final long SKU_ID = 1001L;

    private static SkuSnapshot snapshot(long skuId, String price) {
        return new SkuSnapshot(2001L, skuId, 7L, "示例店铺", "示例商品", "http://img/x.png",
                Map.of("颜色", "黑"), new BigDecimal(price), true, true, true, false);
    }

    private static SkuSnapshot snapshot(long skuId) {
        return snapshot(skuId, "10.00");
    }

    // ── 数量边界 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("数量 0 被拒（0 件不是订单行）")
    void quantityZeroRejected() {
        assertThatThrownBy(() -> OrderItem.open(SKU_ID, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..999");
    }

    @Test
    @DisplayName("数量 -1 被拒")
    void quantityNegativeRejected() {
        assertThatThrownBy(() -> OrderItem.open(SKU_ID, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("数量 1 与 999 是合法边界，1000 被拒（与购物车 @Max(999) 同口径）")
    void quantityBoundaries() {
        assertThat(OrderItem.open(SKU_ID, 1).getQuantity()).isEqualTo(1);
        assertThat(OrderItem.open(SKU_ID, 999).getQuantity()).isEqualTo(999);
        assertThatThrownBy(() -> OrderItem.open(SKU_ID, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("skuId 为空直接拒（连行都算不上）")
    void nullSkuIdRejected() {
        assertThatThrownBy(() -> OrderItem.open(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("刚 open 的行：快照与单价都为空，未完成")
    void freshlyOpenedItemIsIncomplete() {
        OrderItem item = OrderItem.open(SKU_ID, 2);
        assertThat(item.isFulfilled()).isFalse();
        assertThat(item.isPriced()).isFalse();
        assertThat(item.isCompleted()).isFalse();
        assertThat(item.getSpuId()).isNull();
        assertThat(item.getGoodsName()).isNull();
        assertThat(item.getMainImage()).isNull();
        assertThat(item.getSpecAttrs()).isNull();
        assertThat(item.getUnitPrice()).isNull();
        assertThat(item.getSubtotal()).isNull();
    }

    // ── 两段式补全的顺序与一次性 ──────────────────────────────────────────────────

    @Test
    @DisplayName("未 fulfill 就 price → IllegalStateException（这正是「goods-check 必须排在 price-compute 之前」的落点）")
    void priceBeforeFulfillRejected() {
        OrderItem item = OrderItem.open(SKU_ID, 2);
        assertThatThrownBy(() -> item.price(new BigDecimal("10.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("尚未补全商品快照");
        assertThat(item.isPriced()).isFalse();
    }

    @Test
    @DisplayName("重复 fulfill → IllegalStateException（快照是下单那一刻的冻结，不允许被覆盖）")
    void fulfillTwiceRejected() {
        OrderItem item = OrderItem.open(SKU_ID, 2);
        item.fulfill(snapshot(SKU_ID));
        assertThatThrownBy(() -> item.fulfill(snapshot(SKU_ID, "99.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能重复填充");
    }

    @Test
    @DisplayName("快照的 skuId 与本行不一致 → IllegalArgumentException（防「这一行挂着别的商品」的静默数据错）")
    void mismatchedSnapshotRejected() {
        OrderItem item = OrderItem.open(SKU_ID, 1);
        assertThatThrownBy(() -> item.fulfill(snapshot(9999L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    @DisplayName("fulfill 后快照字段被冻结进本行（D15：下单后不再回查商品）")
    void fulfillFreezesSnapshot() {
        OrderItem item = OrderItem.open(SKU_ID, 2);
        item.fulfill(snapshot(SKU_ID));
        assertThat(item.isFulfilled()).isTrue();
        assertThat(item.isPriced()).isFalse();
        assertThat(item.isCompleted()).isFalse();
        assertThat(item.getSpuId()).isEqualTo(2001L);
        assertThat(item.getGoodsName()).isEqualTo("示例商品");
        assertThat(item.getMainImage()).isEqualTo("http://img/x.png");
        assertThat(item.getSpecAttrs()).containsEntry("颜色", "黑");
    }

    @Test
    @DisplayName("规格属性入口即复制：改外部 Map 影响不到本行，本行也改不动")
    void specAttrsAreCopiedAndImmutable() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("颜色", "黑");
        OrderItem item = OrderItem.open(SKU_ID, 1);
        item.fulfill(new SkuSnapshot(2001L, SKU_ID, 7L, "示例店铺", "示例商品", "http://img/x.png",
                mutable, new BigDecimal("10.00"), true, true, true, false));

        mutable.put("颜色", "白");
        assertThat(item.getSpecAttrs()).containsEntry("颜色", "黑");
        assertThatThrownBy(() -> item.getSpecAttrs().put("颜色", "白"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("规格属性为 null 时落成空 Map（不是 null，避免下游 NPE 到处判空）")
    void nullSpecAttrsBecomesEmptyMap() {
        OrderItem item = OrderItem.open(SKU_ID, 1);
        item.fulfill(new SkuSnapshot(2001L, SKU_ID, 7L, "示例店铺", "示例商品", "http://img/x.png",
                null, new BigDecimal("10.00"), true, true, true, false));
        assertThat(item.getSpecAttrs()).isEmpty();
    }

    // ── 单价与小计 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("单价 0.00 / 0.005 被拒（下限 0.01）")
    void unitPriceBelowMinimumRejected() {
        OrderItem item = OrderItem.open(SKU_ID, 1);
        item.fulfill(snapshot(SKU_ID));
        assertThatThrownBy(() -> item.price(new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.01");
        assertThatThrownBy(() -> item.price(new BigDecimal("0.005")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(item.isPriced()).isFalse();
    }

    @Test
    @DisplayName("单价 0.01 是合法下限：0.01 × 999 = 9.99")
    void unitPriceAtMinimumAccepted() {
        OrderItem item = OrderItem.open(SKU_ID, 999);
        item.fulfill(snapshot(SKU_ID));
        item.price(new BigDecimal("0.01"));
        assertThat(item.isCompleted()).isTrue();
        assertThat(item.getSubtotal()).isEqualByComparingTo("9.99");
    }

    @Test
    @DisplayName("小计 = 单价 × 数量，两位小数、四舍五入（1.005 × 3 = 3.015 → 3.02）")
    void subtotalRoundsHalfUp() {
        OrderItem item = OrderItem.open(SKU_ID, 3);
        item.fulfill(snapshot(SKU_ID));
        item.price(new BigDecimal("1.005"));
        assertThat(item.getSubtotal()).isEqualByComparingTo("3.02");
        assertThat(item.getSubtotal().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("常见单价的小计正确（19.90 × 3 = 59.70）")
    void subtotalIsUnitPriceTimesQuantity() {
        OrderItem item = OrderItem.open(SKU_ID, 3);
        item.fulfill(snapshot(SKU_ID));
        item.price(new BigDecimal("19.90"));
        assertThat(item.getSubtotal()).isEqualByComparingTo("59.70");
    }

    @Test
    @DisplayName("单价为 null 被拒")
    void nullUnitPriceRejected() {
        OrderItem item = OrderItem.open(SKU_ID, 1);
        item.fulfill(snapshot(SKU_ID));
        assertThatThrownBy(() -> item.price(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("未 seal 前允许重复定价（纯计算、幂等；真正的闸门在聚合根 seal 之后）")
    void repricingBeforeSealIsAllowed() {
        OrderItem item = OrderItem.open(SKU_ID, 2);
        item.fulfill(snapshot(SKU_ID));
        item.price(new BigDecimal("10.00"));
        item.price(new BigDecimal("12.50"));
        assertThat(item.getUnitPrice()).isEqualByComparingTo("12.50");
        assertThat(item.getSubtotal()).isEqualByComparingTo("25.00");
    }

    // ── 对外不可变：无 public 写入口 ──────────────────────────────────────────────

    @Test
    @DisplayName("对外无任何 public setter，也没有 public 的补全入口（补全只对聚合根可见）")
    void hasNoPublicWriteEntry() {
        assertThat(OrderItem.class.getMethods())
                .as("订单项不允许有任何 public setter")
                .noneMatch(method -> method.getName().startsWith("set"));
        assertThatThrownBy(() -> OrderItem.class.getMethod("fulfill", SkuSnapshot.class))
                .as("fulfill 必须是包内可见，不能对外暴露")
                .isInstanceOf(NoSuchMethodException.class);
        assertThatThrownBy(() -> OrderItem.class.getMethod("price", BigDecimal.class))
                .as("price 必须是包内可见，不能对外暴露")
                .isInstanceOf(NoSuchMethodException.class);
    }
}
