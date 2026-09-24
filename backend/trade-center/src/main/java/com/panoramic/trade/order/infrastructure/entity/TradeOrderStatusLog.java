package com.panoramic.trade.order.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单状态轨迹实体（一行 = 一次状态变更；{@code create_time} 即「发生时刻」）。
 * <p>{@code seq} = **轨迹序号**（这笔单的第几次状态变更，从 0 起）：同一秒内多次变更也能定序，
 * 轨迹顺序不靠时间戳猜。</p>
 * <p>⚠ 它**不是**「状态在主链配置里的下标」：两者在**结束过程收尾**的轨迹上对不上
 * （「待支付→已取消」的末项 seq=1，而「已取消」不在主链上、没有下标），
 * 故 {@code seq} 只表示先后，轨迹合法性由状态机按**路径**判（{@code OrderStatusFlow#assertLegalTrail}）。</p>
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
     * 轨迹序号（这笔单的第几次状态变更，从 0 起；不是「配置里的状态下标」）
     */
    private Integer seq;

    /**
     * 变更后的状态
     */
    private String status;
}
