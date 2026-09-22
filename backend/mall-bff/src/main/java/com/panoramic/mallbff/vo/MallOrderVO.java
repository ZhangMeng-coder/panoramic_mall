package com.panoramic.mallbff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单响应（<b>本端私有类型</b>，{@code POST /orders} / {@code POST /orders/page} /
 * {@code GET /orders/{orderNo}} 的出参元素）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.vo.TradeOrderVO} <b>不是一份东西</b>：
 * 域 VO 是<b>所有调用方共用</b>的（管理端全量视角也在用它），故含 {@code customerId} 与
 * {@code statusStoreAdminLabel}——那两项是商户 / 管理端要的，<b>不得下发 C 端</b>，本类逐字段手工映射时
 * 刻意不带。域 VO 日后加字段不会自动漏到页面，这就是映射必须<b>逐字段写</b>、不用
 * {@code BeanUtils.copyProperties} 的理由。</p>
 *
 * <p>⚠ <b>状态名与文案一律由域下发、本层不重写</b>：{@link #status} 是枚举名（如 {@code PAID}）、
 * {@link #statusMallLabel} 是顾客可读文案（如「已支付」）。同一状态在店主眼里是另一句话
 * （同一枚举的 {@code storeAdminLabel}，如「待发货」）——两端各写一份文案必漂移，故商户端用的那个字段
 * 本类<b>不承接、不下发</b>。</p>
 *
 * <p>⚠ <b>一次提交会按店铺拆成多笔</b>（一单一店），故下单出参是 {@code List<MallOrderVO>}，
 * 顺序 = {@code storeId} 升序（确定）：页面按「一笔一单」展示与支付。</p>
 *
 * <p>⚠ <b>订单标识一律是 {@link #orderNo}</b>（业务可读单号），自增 id 不出现在契约里——
 * 单号已是唯一键，而 id 是拆单顺序的副产品、不稳定。</p>
 */
@Data
public class MallOrderVO {

    /**
     * 业务可读单号（页面上的订单操作 / 详情跳转都以它标识）
     */
    private String orderNo;

    /**
     * 店铺名（<b>下单当时的快照</b>：店铺改名不影响已下的单）
     */
    private String storeName;

    /**
     * 订单来源：{@code DIRECT} / {@code CART}
     */
    private String source;

    /**
     * 状态枚举名（如 {@code PAID}）
     */
    private String status;

    /**
     * 商城端（顾客）可读状态文案（如 {@code PAID} → 「已支付」）——<b>原样取域的，本层不重写</b>
     */
    private String statusMallLabel;

    /**
     * 件数合计（各行 {@code quantity} 之和，不是行数）
     */
    private Integer totalQuantity;

    /**
     * 金额合计（元，两位小数）
     */
    private BigDecimal totalAmount;

    /**
     * 快递单号；<b>未发货为 {@code null}</b>
     */
    private String shipNo;

    /**
     * 下单时间
     */
    private LocalDateTime createTime;

    /**
     * 收货地址快照（下单当时的值：此后顾客改地址 / 删地址都不影响已下的单）
     */
    private Address address;

    /**
     * 订单明细（至少一行；顺序 = 下单时的 {@code skuId} 升序）
     */
    private List<Item> items;

    /**
     * 收货地址快照（<b>嵌套类</b>：页面只读、不单独出现，故不另立一个顶层类型）。
     * <p>⚠ 字段名与域快照 {@code TradeOrderAddressDTO} 刻意不同：那边叫 {@code detail}，
     * 本端照顾客地址的叫法用 {@link #detailAddress}——两个名字不同源，映射处手工对齐。</p>
     */
    @Data
    public static class Address {

        /**
         * 收件人
         */
        private String receiverName;

        /**
         * 收件人电话
         */
        private String receiverPhone;

        /**
         * 省市区
         */
        private String region;

        /**
         * 详细地址
         */
        private String detailAddress;
    }

    /**
     * 订单明细行（<b>嵌套类</b>：不单独出现，只作为 {@link #items} 的元素存在）
     */
    @Data
    public static class Item {

        /**
         * 店铺商品 SKU id（store 域）
         */
        private Long skuId;

        /**
         * 店铺商品 SPU id（store 域）
         */
        private Long spuId;

        /**
         * 商品名（<b>下单当时的快照</b>）
         */
        private String goodsName;

        /**
         * 商品主图（<b>下单当时的快照</b>）；无图时为 {@code null}
         * <p>⚠ <b>空串也可能出现</b>（商品域原样落库，这一层不归一）——前端判无图请用 falsy
         * （{@code !mainImage}）：只判 {@code === ''} 会漏掉 {@code null}，
         * 只判 {@code == null} 会漏掉空串。</p>
         */
        private String mainImage;

        /**
         * 规格（规格名 → 取值，如「颜色」→「黑」）；无规格为空表，不是 {@code null}
         */
        private Map<String, String> specAttrs;

        /**
         * 单价（元，下单当时的快照）
         */
        private BigDecimal unitPrice;

        /**
         * 数量
         */
        private Integer quantity;

        /**
         * 小计（= 单价 × 数量，<b>下单当时算好落库的值</b>，不由页面乘——页面再乘一遍就是第二份口径）
         */
        private BigDecimal subtotal;
    }
}
