package com.panoramic.trade.order.infrastructure.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmission;
import com.panoramic.trade.order.infrastructure.mapper.TradeOrderSubmissionMapper;
import com.panoramic.trade.order.infrastructure.service.TradeOrderSubmissionService;
import org.springframework.stereotype.Service;

/**
 * 订单提交记录服务实现（L1 请求级幂等）。
 * <p>只建基座：CRUD 走 MP 基类（{@code ServiceImpl}），本类不写自定义方法。
 * 审计字段由 common 的 {@code MyMetaObjectHandler} 经 {@code UserContext} 自动填充，本类不显式赋值。</p>
 */
@Service
public class TradeOrderSubmissionServiceImpl extends ServiceImpl<TradeOrderSubmissionMapper, TradeOrderSubmission>
        implements TradeOrderSubmissionService {
}
