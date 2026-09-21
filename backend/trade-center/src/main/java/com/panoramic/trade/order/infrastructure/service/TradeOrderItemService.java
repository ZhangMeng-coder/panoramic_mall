package com.panoramic.trade.order.infrastructure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderItem;

/**
 * 订单明细服务。
 * <p>只建基座：own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法，本接口不声明自定义方法。</p>
 */
public interface TradeOrderItemService extends IService<TradeOrderItem> {
}
