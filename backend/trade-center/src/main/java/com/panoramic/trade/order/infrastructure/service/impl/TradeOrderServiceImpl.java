package com.panoramic.trade.order.infrastructure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.trade.order.infrastructure.entity.TradeOrder;
import com.panoramic.trade.order.infrastructure.mapper.TradeOrderMapper;
import com.panoramic.trade.order.infrastructure.service.TradeOrderService;
import org.springframework.stereotype.Service;

/**
 * 订单主表服务实现。
 * <p>只建基座：CRUD 走 MP 基类（{@code ServiceImpl}），本类不写自定义方法。
 * 审计字段由 common 的 {@code MyMetaObjectHandler} 经 {@code UserContext} 自动填充，本类不显式赋值。</p>
 */
@Service
public class TradeOrderServiceImpl extends ServiceImpl<TradeOrderMapper, TradeOrder>
        implements TradeOrderService {
}
