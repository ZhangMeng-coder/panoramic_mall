package com.panoramic.trade.order.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 提交记录 ↔ 订单关联实体（一次请求落了哪几笔单）。
 * <p>纯关联表，物理删除，无审计列。</p>
 */
@Data
@TableName("trade_order_submission_order")
public class TradeOrderSubmissionOrder {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * trade_order_submission.id
     */
    private Long submissionId;

    /**
     * trade_order.order_no
     */
    private String orderNo;
}
