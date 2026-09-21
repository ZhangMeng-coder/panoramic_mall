package com.panoramic.trade.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.trade.order.infrastructure.entity.TradeOrder;

/**
 * 订单主表 Mapper。
 * <p>只建基座：own-entity 的增删改查一律走 MP 基类（{@code BaseMapper}），无自定义方法。</p>
 */
public interface TradeOrderMapper extends BaseMapper<TradeOrder> {
}
