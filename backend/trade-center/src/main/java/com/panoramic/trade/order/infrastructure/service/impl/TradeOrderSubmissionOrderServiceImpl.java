package com.panoramic.trade.order.infrastructure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmissionOrder;
import com.panoramic.trade.order.infrastructure.mapper.TradeOrderSubmissionOrderMapper;
import com.panoramic.trade.order.infrastructure.service.TradeOrderSubmissionOrderService;
import org.springframework.stereotype.Service;

/**
 * 提交记录↔订单关联服务实现（纯关联表，物理删除）。
 * <p>只建基座：CRUD 走 MP 基类（{@code ServiceImpl}），本类不写自定义方法。</p>
 */
@Service
public class TradeOrderSubmissionOrderServiceImpl extends ServiceImpl<TradeOrderSubmissionOrderMapper, TradeOrderSubmissionOrder>
        implements TradeOrderSubmissionOrderService {
}
