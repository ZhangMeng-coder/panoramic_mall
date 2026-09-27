package com.panoramic.trade.order.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * 收货后的销量推送：接 {@link OrderSalesPushEvent}，在<b>事务提交之后</b>、于<b>自定义线程池</b>上推给 store 域。
 *
 * <h3>两个注解各解决一件事，缺一不可</h3>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)}：<b>正确性</b>。发布点在收货那个事务里，
 *       此刻订单行还没提交——直接推会让本服务读到「已发货」的旧状态并跳过推送，而
 *       {@code sales_pushed} 仍是 0，只能等兜底扫描（最坏晚一轮扫描）。见 {@link OrderSalesPushEvent}。
 *       ⚠ {@code fallbackExecution = true}：没有活跃事务时照常执行（收货必带事务，这一项是给
 *       「将来别处在非事务路径上补发事件」留的口子——静默跳过是最难查的一类漏推）。</li>
 *   <li>{@code @Async("salesPushExecutor")}：<b>不阻塞收货</b>。⚠ 这条不是优化而是要求
 *       （需求：<b>推送失败不得阻塞收货</b>）：AFTER_COMMIT 的回调跑在**请求线程**上，
 *       不异步就是「顾客点完收货，还要等一次 Feign 往返才拿到响应」——store 域慢或挂住时，
 *       这个等待会长达熔断的超时阈值。异步之后请求立刻返回，推送在后台线程上跑。</li>
 * </ul>
 *
 * <h3>失败一律不外抛，只留未推送标记</h3>
 * <p>本方法 catch 掉一切 {@code RuntimeException}：推送失败<b>不是</b>收货的失败——收货事务早已提交，
 * 这里再把异常抛出去只会污染一个已经成功的请求（且 {@code @Async} 下它本来也回不到调用方）。
 * 失败后 {@code sales_pushed} 仍是 0（标记与推送在同一处、且标记在后），
 * 由 {@link OrderSalesPushTask} 下一轮重捞。故「推送丢失」不是一个需要重试机制处理的问题——
 * 重试就是下一轮扫描。</p>
 * <p>⚠ <b>对方明确拒绝（400/404）也走同一支</b>，即下一轮还会再推一次。这是有意的：
 * store 侧的销量接口只有台账唯一键一种拒绝，而唯一键冲突在那边是 no-op 成功、不是错误，
 * 故正常的「拒」不存在；真的出现就说明两侧的报文口径分岔了，那件事该由日志里的 warn 暴露出来，
 * 而不是靠「不再重推」把它静默掉（不重推 = 销量永久少记，且没人会知道）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSalesPushListener {

    private final OrderSalesPushService orderSalesPushService;

    /**
     * 推一笔已完成订单的销量（事务提交后、线程池上执行；失败只记日志）
     *
     * @param event 订单完成事件（携带单号）
     */
    @Async("salesPushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderReceived(OrderSalesPushEvent event) {
        String orderNo = event.orderNo();
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        try {
            orderSalesPushService.pushByOrderNo(orderNo);
        } catch (RuntimeException e) {
            // 系统错（store 不可达 / 熔断打开 / 数据被写坏）：带堆栈记 warn，**吞掉**——
            // 收货事务已提交，这里抛出只会污染请求。未推送标记还在，兜底扫描会重捞，故无需重试
            log.warn("销量推送失败，留给兜底扫描：orderNo={}", orderNo, e);
        }
    }
}
