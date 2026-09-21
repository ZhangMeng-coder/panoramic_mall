package com.panoramic.trade.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmissionOrder;

/**
 * 提交记录↔订单关联 Mapper（纯关联表，物理删除）。
 * <p>只建基座：own-entity 的增删改查一律走 MP 基类（{@code BaseMapper}），无自定义方法。</p>
 */
public interface TradeOrderSubmissionOrderMapper extends BaseMapper<TradeOrderSubmissionOrder> {
}
