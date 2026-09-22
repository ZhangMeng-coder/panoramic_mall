package com.panoramic.contract.trade.vo;

import com.panoramic.contract.trade.dto.TradeOrderAddressDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 订单响应（trade-center 域内部接口与端 BFF 同源共享）。
 *
 * <p>⚠ <b>订单标识一律是 {@code orderNo}</b>（业务可读单号），自增 id 不出现在契约里：
 * 单号已是唯一键、由「生成 → 查重 → 重试」保证不撞；用 id 会让页面契约依赖一个落库后才存在、
 * 且不稳定（拆单顺序决定）的东西。三个写动作（支付 / 发货 / 收货）出参是 {@code void}，
 * 页面重拉列表或详情拿新状态——**不再给它们各配一份本 VO**（同一份形状的第二个出口）。</p>
 *
 * <p>⚠ <b>状态文案由本 VO 下发</b>（{@link #statusMallLabel} / {@link #statusStoreAdminLabel}）：
 * 同一个状态在顾客眼里与在店主眼里是两件事（如 {@code PAID}——顾客看到「已支付」、店主看到「待发货」），
 * 两端的 BFF <b>都不要重写文案</b>——各写一份必漂移。哪一侧展示哪个字段由各端自己选。</p>
 *
 * <p>⚠ <b>这里含 {@code customerId}，与 {@code TradeCartItemVO} 刻意不含它不同</b>：
 * 购物车里顾客 id 就是查询条件本身（回传只会让调用方误以为「域侧判过归属」）；
 * 而订单是**所有调用方共用**一份 VO——管理端是全量视角，需要知道这笔单是谁下的；
 * 少了它，管理端只能靠再调一次别的接口才知道订单属于谁。</p>
 */
@Data
public class TradeOrderVO {

    /**
     * 业务可读单号（18 位；页面上的一切订单操作都以它标识）
     */
    private String orderNo;

    /**
     * 下单顾客 id（= {@code mall_user.id}，跨域 id 引用、无外键）
     */
    private Long customerId;

    /**
     * 所属店铺 id（= 店主账号 id，跨域 id 引用、无外键）
     */
    private Long storeId;

    /**
     * 店铺名（**下单当时的快照**：店铺改名不影响已下的单）
     */
    private String storeName;

    /**
     * 订单来源：{@code DIRECT} / {@code CART}（枚举名，与 {@code TradeOrderCreateDTO#getSource()} 同取值）
     */
    private String source;

    /**
     * 状态（枚举名，如 {@code PAID}）
     */
    private String status;

    /**
     * 商城端（顾客）可读文案（如 {@code PAID} → 「已支付」）
     */
    private String statusMallLabel;

    /**
     * 商户端 / 管理端可读文案（如 {@code PAID} → 「待发货」）
     */
    private String statusStoreAdminLabel;

    /**
     * 件数合计（各行 quantity 之和，不是行数）
     */
    private Integer totalQuantity;

    /**
     * 金额合计（元，两位小数）
     */
    private BigDecimal totalAmount;

    /**
     * 快递单号；**未发货为 null**
     */
    private String shipNo;

    /**
     * 下单时刻（L2 幂等窗口的基准，也是页面上的「下单时间」）
     */
    private LocalDateTime createTime;

    /**
     * 收货地址快照（下单当时的值；复用下单那份形状，见 {@link TradeOrderAddressDTO}）
     */
    private TradeOrderAddressDTO address;

    /**
     * 订单明细（至少一行；顺序 = 下单时的 skuId 升序）
     */
    private List<Item> items;

    /**
     * 订单明细行（**刻意是嵌套类**：契约表第三节的 vo 清单里没有独立的明细类型——
     * 它不单独出现，只作为 {@link #items} 的元素存在）
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
         * 商品名（**下单当时的快照**）
         */
        private String goodsName;

        /**
         * 商品主图（**下单当时的快照**）；无图时为 {@code null}（列 {@code DEFAULT NULL}），
         * ⚠ 但**空串也可能出现**（商品域原样落库，不在这一层归一）——前端判无图请用 falsy
         * （{@code !mainImage}），只判 {@code === ''} 会漏掉 {@code null}、只判 {@code == null} 会漏掉空串
         */
        private String mainImage;

        /**
         * 规格（规格名 → 取值，如「颜色」→「黑」）；无规格为空表，不是 null
         */
        private Map<String, String> specAttrs;

        /**
         * 单价（元，下单当时的快照）
         */
        private BigDecimal unitPrice;

        /**
         * 数量：1..999
         */
        private Integer quantity;

        /**
         * 小计（= 单价 × 数量，下单当时算好落库的值，不由页面乘）
         */
        private BigDecimal subtotal;
    }
}
