package com.panoramic.trade.order.support;

import com.panoramic.trade.order.domain.OrderStatus;

import java.util.List;

/**
 * 测试用的状态流转**主链**：与 {@code src/main/resources/application.yml} 的 {@code status-flow} 同构。
 *
 * <p>⚠ <b>只有主链</b>（2026-09-24 起）：与生产配置一样，这里**不写** CANCELLED / REFUNDED ——
 * 那两个「结束过程」（待支付 → 已取消、已支付 → 已退款）写死在 {@code OrderModel} 的
 * {@code markCancelled} / {@code markRefunded} 里，配进来反而会被状态机构造器拒绝。
 * 故「测试里的完整流转」= 本夹具给出的主链 + 动作方法里那两个结束过程。</p>
 *
 * <p>⚠ 为什么抽成一处：这份主链是**十二个测试类共用的夹具**（下单编排 / 流水线 / 幂等 / 拆单 / 回滚 /
 * 编号 / 作用域护栏 / 取消 / 支付超时 / 关单任务 / 状态机 / 聚合根），抄十遍的话改一次口径要改十处、
 * 漏一处只在运行时红。「真实 yml ↔ 代码」的对账不在这里做——那是 {@code OrderDomainWiringTest} 的活
 * （它直读那份 yml），本类只管「测试里要一份合法的配置」。</p>
 *
 * <p>⚠ 返回的是**不可变列表**：要按用例改几笔的（如状态机的负例）自己
 * {@code new ArrayList<>(OrderStatusChain.production())} 拷一份再改。</p>
 */
public final class OrderStatusChain {

    /** 与生产配置同构：首项（= 入口）是待支付；**只有主链**（两个结束过程由动作补，见类注释） */
    public static List<OrderStatus> production() {
        return List.of(
                OrderStatus.PENDING_PAYMENT,
                OrderStatus.PAID,
                OrderStatus.SHIPPED,
                OrderStatus.RECEIVED);
    }

    private OrderStatusChain() {
    }
}
