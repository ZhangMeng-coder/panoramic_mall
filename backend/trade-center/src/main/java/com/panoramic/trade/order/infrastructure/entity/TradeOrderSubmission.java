package com.panoramic.trade.order.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单提交记录（一次下单请求一行；唯一键 {@code (customer_id, request_id)} 即 L1 请求级幂等键）。
 * <p>⚠ 本表 {@code is_delete} 恒 0：提交记录不删行，该列只为对齐 {@link BaseEntity} 而保留。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本实体不显式赋值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order_submission")
public class TradeOrderSubmission extends BaseEntity {

    /**
     * 主键（IdType.AUTO：由 DB 自增生成）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 下单顾客（= mall_user.id）
     */
    private Long customerId;

    /**
     * 端 BFF 提交的请求号（L1 幂等键，与 customerId 一起构成唯一键 uk_customer_request）
     */
    private String requestId;
}
