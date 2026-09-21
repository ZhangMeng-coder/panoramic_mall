package com.panoramic.trade.order.infrastructure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmissionOrder;

/**
 * 提交记录↔订单关联服务（纯关联表，物理删除）。
 * <p>只建基座：own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法，本接口不声明自定义方法。</p>
 */
public interface TradeOrderSubmissionOrderService extends IService<TradeOrderSubmissionOrder> {
}
