package com.panoramic.trade.order.application;

import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.SalesPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 销量推送（订单完成后把该单各 SPU 的合计件数推给 store 域）——**快速路径与兜底扫描共用的唯一一处**。
 *
 * <p>两个入口都落到 {@link #push(OrderModel)}：{@link #pushByOrderNo} 供收货后的监听器（手上只有单号）、
 * {@link #push} 供兜底扫描（手上已经有整批订单聚合）。写成两条各自实现的路径时，「兜底」就成了
 * 第二份口径——推送字段多一个、少一个都不会有任何东西报错，只会让补推出去的报文与快路径不同。</p>
 *
 * <h3>为什么没有 {@code @Transactional}</h3>
 * <p>三步（读单 → 推送 → 标记）**故意不在一个事务里**：</p>
 * <ul>
 *   <li>中间的推送是一次跨服务网络调用，把它包进事务等于持着 DB 连接等网络——下游慢就拖住连接池；</li>
 *   <li>更根本的是**它不该回滚**：推送失败时收货已经成功，把读取与标记一起回滚并不能撤销那笔收货，
 *       只会让「重推」的依据（{@code sales_pushed} 仍是 0）白丢一次写入；</li>
 *   <li>读侧也不需要事务：目标单必须是 {@code RECEIVED}，而它是主链终点——读到的状态不会在下一次查询时变掉，
 *       三次查询（订单行 / 明细 / 轨迹）之间的对账因此不依赖同一个快照
 *       （与 {@code pageOrders} / {@code getOrder} 的只读事务不同：那两条读的是**会动**的单）。</li>
 * </ul>
 * <p>⚠ 于是「标记」这一步必须在推送**成功之后**才发生（顺序不能反，见
 * {@link OrderRepository#markSalesPushed}）——它是本类唯一的正确性关键点。</p>
 *
 * <h3>重复推送是允许的</h3>
 * <p>快路径与兜底扫描可能撞同一笔单（推送慢、或推成功但标记失败）。撞了不会错账：
 * store 域的台账唯一键 {@code (order_no, spu_id)} 把重复吸收成 no-op 成功。
 * 本类因此**不做**「推过就跳过」的判断——单号上的「推过」查不出来（标记是唯一落点，
 * 而标记为 0 恰恰说明该推），查到了也不比让对方忽略更省事。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSalesPushService {

    private final OrderRepository orderRepository;
    private final SalesPort salesPort;

    /**
     * 按单号推销量（先读单、再走 {@link #push}）——收货后的快速路径用
     *
     * <p>⚠ 读单**不带作用域**（两个作用域都传 null = 全量视角）：本方法是域内的**系统级动作**
     * （触发方是收货的提交后回调与兜底任务，都不是任何页面能力），域内不需要、也不该有一个「顾客 id」
     * 才能读它自己刚写完的那一笔单——与 {@code OrderTimeoutCloseService} 同一口径。</p>
     *
     * @param orderNo 业务可读单号
     * @return 是否真的推了（{@code false} = 订单不存在或不是已完成态，跳过）
     * @throws RuntimeException store 域不可达 / 熔断打开 / 对方业务失败（由调用方决定吞还是报）
     */
    public boolean pushByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return false;
        }
        OrderModel order = orderRepository.findOrder(OrderQuery.forDetail(orderNo, null, null)).orElse(null);
        if (order == null) {
            // 订单不删行，正常到不了这里。能到只能是「事件投递晚了、单被手工删了」这类运维干预
            log.warn("销量推送跳过：订单不存在。orderNo={}", orderNo);
            return false;
        }
        return push(order);
    }

    /**
     * 推一笔订单的销量，成功即标记（兜底扫描的入参已经是聚合，故直接用它）
     *
     * @param order 待推送的订单
     * @return 是否真的推了（{@code false} = 不是已完成态，跳过）
     * @throws RuntimeException 推送失败（含 store 侧业务失败与原样上抛的序列化错）
     */
    public boolean push(OrderModel order) {
        if (order.getStatus() != OrderStatus.RECEIVED) {
            // ⚠ 唯一一处「不推」的判据：销量只记**订单完成**的件数（需求裁定）。
            //    兜底扫描的 SQL 已经按状态收窄过，故这一支在正常路径上到不了——留着是因为本方法
            //    是 public 的、且它是那条口径的**最后一道**：把它放在 SQL 里、代码里不写，
            //    换个调用方（或将来某天 SQL 改错了）就会把没完成的单记成销量，而销量不退。
            log.warn("销量推送跳过：订单不是已完成态。orderNo={}，status={}", order.getOrderNo(), order.getStatus());
            return false;
        }
        salesPort.pushSales(order);
        // ⚠ 推成功之后才标记（顺序不能反，见 OrderRepository#markSalesPushed）
        orderRepository.markSalesPushed(order.getOrderNo());
        return true;
    }
}
