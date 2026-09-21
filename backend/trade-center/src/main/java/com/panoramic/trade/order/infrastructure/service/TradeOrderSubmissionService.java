package com.panoramic.trade.order.infrastructure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmission;

/**
 * 订单提交记录服务（L1 请求级幂等）。
 * <p>只建基座：own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法，本接口不声明自定义方法。</p>
 */
public interface TradeOrderSubmissionService extends IService<TradeOrderSubmission> {
}
