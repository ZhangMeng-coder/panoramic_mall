package com.panoramic.trade.order.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单状态轨迹实体（一行 = 一次状态变更；{@code create_time} 即「发生时刻」）。
 * <p>{@code seq} = 状态下标（status-flow 配置里的位置，从 0 起）：同一秒内多次变更也能定序，
 * 轨迹顺序不靠时间戳猜。</p>
 * <p>⚠ 本表 {@code is_delete} 恒 0：轨迹不删行，该列只为对齐 {@link BaseEntity} 而保留。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本实体不显式赋值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order_status_log")
public class TradeOrderStatusLog extends BaseEntity {

    /**
     * 主键（IdType.AUTO：由 DB 自增生成）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * trade_order.order_no
     */
    private String orderNo;

    /**
     * 状态下标（status-flow 配置里的位置，从 0 起）
     */
    private Integer seq;

    /**
     * 变更后的状态
     */
    private String status;
}
