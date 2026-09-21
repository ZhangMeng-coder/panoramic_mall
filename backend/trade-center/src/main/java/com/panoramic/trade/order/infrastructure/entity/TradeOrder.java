package com.panoramic.trade.order.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单主表实体（一笔订单一店）。
 * <p>收件人四项为下单时的地址快照；{@code fingerprint} 是 L2 批次指纹（窗口内命中即复用既有单）。</p>
 * <p>⚠ 本表 {@code is_delete} 恒 0：订单不删行，该列只为对齐 {@link BaseEntity} 而保留。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本实体不显式赋值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order")
public class TradeOrder extends BaseEntity {

    /**
     * 主键（IdType.AUTO：由 DB 自增生成）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 18 位业务单号（yyyyMMddHHmmss+4位序列），对外唯一标识
     */
    private String orderNo;

    /**
     * 下单顾客（= mall_user.id）
     */
    private Long customerId;

    /**
     * 店铺（= store_shop.id）
     */
    private Long storeId;

    /**
     * 下单时店铺名快照
     */
    private String storeName;

    /**
     * 下单来源：DIRECT（详情页直购）/ CART（购物车结算）
     */
    private String source;

    /**
     * 首次创建本单的请求号（L2 复用单保留原值）
     */
    private String requestId;

    /**
     * 批次指纹 sha256 前 16 位（L2 幂等键，不含金额与时间）
     */
    private String fingerprint;

    /**
     * 订单状态：PENDING_PAYMENT/PAID/SHIPPED/RECEIVED
     */
    private String status;

    /**
     * 总件数（= Σ明细数量，封存时对账）
     */
    private Integer totalQuantity;

    /**
     * 订单总额（= Σ明细小计，封存时对账）
     */
    private BigDecimal totalAmount;

    /**
     * 收件人（下单时地址快照）
     */
    private String receiverName;

    /**
     * 收件人电话（下单时地址快照）
     */
    private String receiverPhone;

    /**
     * 省市区（下单时地址快照）
     */
    private String receiverRegion;

    /**
     * 详细地址（下单时地址快照）
     */
    private String receiverDetail;

    /**
     * 快递单号（发货时录入）
     */
    private String shipNo;
}
