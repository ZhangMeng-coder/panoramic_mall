package com.panoramic.trade.order.domain;

import com.panoramic.trade.order.domain.port.SkuSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;

/**
 * 订单项：**聚合内部可补全、对外不可变**。
 *
 * <p>它是「模型封闭性」的承担者（todo 任务提示 1）：一个订单项在业务上分两步才长齐——先由
 * {@code goods-check} 补商品快照（{@link #fulfill}），再由 {@code price-compute} 补单价与小计（{@link #price}）。
 * 若按「构造时一次性给全所有字段」写，装配顺序就只能靠调用方自觉（先查商品再算价），
 * <b>顺序错了不会有任何编译期或运行期信号</b>；而按两段式写，未补快照就计价会直接抛错——
 * 于是「goods-check 必须排在 price-compute 之前」成了模型的**不变量**，而不是流程文档里的一句话。</p>
 *
 * <p>⚠ <b>对外无任何 public setter</b>：补全方法 {@link #fulfill} / {@link #price} 都是包内可见，
 * 只有同包的聚合根 {@link OrderModel} 能调到，且聚合根会在自己 seal 之后拒绝再调（改行一律抛
 * {@code IllegalStateException}）。这就是「领域对象不给外部改」的落法——不是靠约定，是靠可见性。</p>
 *
 * <p>⚠ 为什么没有 {@code equals/hashCode}：本对象有**生命周期**（补全前与补全后是两个状态），
 * 按字段比会得到「同一行不相等」的假象；行的身份是 {@code skuId}（聚合根按它查找），需要判身份时
 * 用 {@link #getSkuId()} 比，不要依赖对象相等。</p>
 *
 * <p>快照字段（{@code goodsName}/{@code mainImage}/{@code specAttrs}）冻结在此处正是裁定 D15 的落点
 * ——下单后不再回查商品，商品日后改名换图都不影响已有订单。</p>
 *
 * <p>⚠ 落库期（2026-09-21）多了一个反向入口 {@link #rehydrate}：从落库状态把订单项重建出来。
 * 它与 {@link #open} 的两段式补全**对称**——一个向外建、一个向内读，两条路的校验口径同一份
 * （{@code rehydrate} 复用 {@link #price} 的单价校验与小计算法）。</p>
 */
public final class OrderItem {

    /** 单行数量下限：0 件不是订单行，是「没买」 */
    public static final int MIN_QUANTITY = 1;

    /** 单行数量上限：与购物车 DTO 的 {@code @Max(999)} 同口径，避免两条入口出现两个上限 */
    public static final int MAX_QUANTITY = 999;

    /** 单价下限：0.01 元。0 元是赠品语义，本期不做（真要做应是「赠品行」而不是让价格字段为 0） */
    public static final BigDecimal MIN_UNIT_PRICE = new BigDecimal("0.01");

    /** 金额一律两位小数，四舍五入（与库的 DECIMAL(10,2) 同口径，避免落库时二次舍入对不上） */
    private static final int AMOUNT_SCALE = 2;

    private final Long skuId;
    private final int quantity;

    // ── 以下由聚合内部补全（两段式生命周期的产物），对外只读 ──────────────────────────
    private Long spuId;
    private String goodsName;
    private String mainImage;
    private Map<String, String> specAttrs;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;

    private boolean fulfilled;
    private boolean priced;

    private OrderItem(Long skuId, int quantity) {
        this.skuId = skuId;
        this.quantity = quantity;
    }

    /**
     * 开出一行「只有 id 与数量」的订单项（两段式生命周期的第一段）
     *
     * <p>商品快照与单价此时都为空，必须由 {@link #fulfill} 与 {@link #price} 补齐后聚合根才允许 seal。</p>
     *
     * @param skuId    店铺 SKU id
     * @param quantity 购买数量，取值 {@code 1..999}
     * @return 未补全的订单项
     * @throws IllegalArgumentException 数量不在 {@code 1..999}（领域业务参数错误，直接告诉调用方错在哪）
     */
    public static OrderItem open(Long skuId, int quantity) {
        Objects.requireNonNull(skuId, "订单项的 skuId 不能为空");
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new IllegalArgumentException("商品数量必须在 " + MIN_QUANTITY + ".." + MAX_QUANTITY + " 之间，实际为 " + quantity);
        }
        return new OrderItem(skuId, quantity);
    }

    /**
     * 补全商品快照（由 goods-check 步骤经聚合根调用）
     *
     * <p>⚠ 重复调用一律抛 {@link IllegalStateException}：快照是「下单那一刻」的冻结（裁定 D15），
     * 允许覆盖等于允许两个时刻的快照混在一单里。所以这里不做「后写覆盖先写」，直接拒绝。</p>
     *
     * @param snapshot 商品快照；其 {@code skuId} 必须与本项一致
     * @throws IllegalStateException    本项快照已补全
     * @throws IllegalArgumentException 快照的 skuId 与本项不一致（那是调用方把快照配错了行，属编程错误，
     *                                  若静默接受会让订单项挂着**别的商品**的名字与图，是「不报错但写坏数据」）
     */
    void fulfill(SkuSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "商品快照不能为空");
        if (fulfilled) {
            throw new IllegalStateException("订单项 skuId=" + skuId + " 的商品快照已补全，不能重复填充");
        }
        if (!Objects.equals(snapshot.skuId(), skuId)) {
            throw new IllegalArgumentException("商品快照的 skuId=" + snapshot.skuId() + " 与订单项的 skuId=" + skuId + " 不一致");
        }
        this.spuId = snapshot.spuId();
        this.goodsName = snapshot.goodsName();
        this.mainImage = snapshot.mainImage();
        // 规格属性是可变的 Map：入口就 copy 成不可变，之后无论谁拿到引用都改不动这一行
        this.specAttrs = snapshot.specAttrs() == null ? Map.of() : Map.copyOf(snapshot.specAttrs());
        this.fulfilled = true;
    }

    /**
     * 按单价算出小计（由 price-compute 步骤经聚合根调用）
     *
     * <p>⚠ <b>必须先 {@link #fulfill}</b>：这就是「goods-check 排在 price-compute 之前」的落点——
     * 未确认商品可购买就算钱，等于为一件可能已下架的商品生成订单，故未补快照即计价一律抛
     * {@link IllegalStateException}（这不是用户输入问题，是装配顺序错了，属编程错误）。</p>
     *
     * <p>⚠ 与 {@code fulfill} 不同，**未 seal 前允许重复定价**：单价来自商品域的只读快照，重算小计是
     * 幂等的纯计算（同一入参得同一结果），为它设一次性闸门只会让「步骤重跑」这种正常重试变成故障。
     * 真正的闸门在聚合根 seal 之后：那时任何改行入口都会被拒。</p>
     *
     * @param unitPrice 商品单价，必须 {@code >= 0.01}
     * @throws IllegalStateException    尚未补全商品快照
     * @throws IllegalArgumentException 单价为 null 或低于 {@code 0.01}
     */
    void price(BigDecimal unitPrice) {
        Objects.requireNonNull(unitPrice, "商品单价不能为空");
        if (!fulfilled) {
            throw new IllegalStateException("订单项 skuId=" + skuId + " 尚未补全商品快照，不能先定价");
        }
        if (unitPrice.compareTo(MIN_UNIT_PRICE) < 0) {
            throw new IllegalArgumentException("商品单价不得低于 " + MIN_UNIT_PRICE.toPlainString() + " 元，实际为 " + unitPrice.toPlainString());
        }
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        this.priced = true;
    }

    /**
     * 从落库状态重建一个**已补全**的订单项（⚠ 只有仓储适配器该调它）
     *
     * <p>⚠ 公开可见性是**跨包**的要求，不是「谁都能调」的邀请：仓储适配器在
     * {@code infrastructure.jdbc}，与 domain 不同包（同 {@link OrderModel#rehydrate}）。</p>
     *
     * <p>⚠ 复用 {@link #open} 与 {@link #price} 而不是直接赋值：数量区间、单价下限、小计算法
     * 与建单路径**同一份**实现。若这里另写一遍，两条路的校验就会各自漂移，
     * 而漂移的后果是「库里读出来的东西与写进去的规则不一致」——最难发现的一类。</p>
     *
     * <p>⚠ 落库的小计**参与对账**：它与「单价 × 数量」必须相等，不等说明读出来的列串了位
     * （例如 {@code unit_price} 与 {@code subtotal} 互换），这类错误不查就一路带到页面上。</p>
     *
     * @param skuId     店铺 SKU id
     * @param quantity  数量
     * @param spuId     店铺商品 SPU id
     * @param goodsName 下单时冻结的商品名
     * @param mainImage 下单时冻结的主图（可为 null：商品本来就没图）
     * @param specAttrs 下单时冻结的规格（可为 null → 记为空 Map）
     * @param unitPrice 下单时单价
     * @param subtotal  落库的小计（用于对账）
     * @return 已补全（{@code fulfilled=true, priced=true}）的订单项
     * @throws IllegalStateException 落库的小计与按单价重算的结果不一致
     */
    public static OrderItem rehydrate(Long skuId, int quantity, Long spuId, String goodsName, String mainImage,
                                      Map<String, String> specAttrs, BigDecimal unitPrice, BigDecimal subtotal) {
        OrderItem item = open(skuId, quantity);
        item.spuId = Objects.requireNonNull(spuId, "订单项的 spuId 不能为空");
        item.goodsName = Objects.requireNonNull(goodsName, "订单项的商品名不能为空");
        item.mainImage = mainImage;
        item.specAttrs = specAttrs == null ? Map.of() : Map.copyOf(specAttrs);
        item.fulfilled = true;
        item.price(unitPrice);
        if (subtotal == null || subtotal.compareTo(item.subtotal) != 0) {
            throw new IllegalStateException("订单项 skuId=" + skuId + " 落库的小计("
                    + (subtotal == null ? "null" : subtotal.toPlainString()) + ")与按单价重算的("
                    + item.subtotal.toPlainString() + ")不一致");
        }
        return item;
    }

    /**
     * 本项是否已补全（快照 + 单价都齐备）——聚合根 seal 的门槛
     *
     * @return 两项都完成才为 {@code true}
     */
    public boolean isCompleted() {
        return fulfilled && priced;
    }

    /**
     * 商品快照是否已补全（goods-check 之后的阶段判据；供装配顺序的断言使用）
     *
     * @return 已 {@code fulfill} 则为 {@code true}
     */
    public boolean isFulfilled() {
        return fulfilled;
    }

    /**
     * 单价与小计是否已算出（price-compute 之后的阶段判据）
     *
     * @return 已 {@code price} 则为 {@code true}
     */
    public boolean isPriced() {
        return priced;
    }

    /**
     * @return SKU id（本项在聚合内的身份，聚合根按它查找）
     */
    public Long getSkuId() {
        return skuId;
    }

    /**
     * @return SPU id；补全前为 {@code null}
     */
    public Long getSpuId() {
        return spuId;
    }

    /**
     * @return 下单时冻结的商品名；补全前为 {@code null}
     */
    public String getGoodsName() {
        return goodsName;
    }

    /**
     * @return 下单时冻结的商品主图 URL；补全前为 {@code null}
     */
    public String getMainImage() {
        return mainImage;
    }

    /**
     * @return 下单时冻结的规格属性（只读；无规格时为空 Map，不是 null）
     */
    public Map<String, String> getSpecAttrs() {
        return specAttrs;
    }

    /**
     * @return 商品单价；未定价前为 {@code null}
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /**
     * @return 购买数量（{@code 1..999}）
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * @return 小计 = 单价 × 数量（两位小数、四舍五入）；未定价前为 {@code null}
     */
    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
