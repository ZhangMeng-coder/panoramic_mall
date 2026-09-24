package com.panoramic.trade.order.application;

import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.StockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 终止一笔订单并把库存还回去：<b>取消</b>（{@code PENDING_PAYMENT → CANCELLED}）与
 * <b>仅退款</b>（{@code PAID → REFUNDED}）共用这一份实现。
 *
 * <h3>为什么取消与仅退款是同一个服务</h3>
 * <p>两个动作在域侧的**动作**完全一样：把状态推到某个终态、落库、回补库存。差异只有两个——
 * 目标状态、以及「允许从哪个状态来」（后者不在本类里，是状态机上的边）。
 * 各写一份的代价不是多几行，而是「回补库存」这件事变成两处实现：迁移一步、漏一处就是少卖。</p>
 *
 * <h3>为什么它被抽成独立服务，而不是写在 {@code OrderApplicationService#cancelOrder} 里</h3>
 * <p><b>因为这笔动作有两个触发方</b>：C 端顾客主动取消（经 BFF 进来的那个接口）、
 * 以及域内的**超时未支付自动关单定时任务**。定时任务不经 Controller、也不该为了复用而绕一圈 HTTP，
 * 它要的就是「这笔单按取消处理」这一件事。口径只有一份，触发方各自去找它。</p>
 *
 * <h3>三步的顺序不可颠倒</h3>
 * <ol>
 *   <li><b>聚合内迁移</b>——先让状态机判（非法迁移在这里就 400 了，一个字节都不会写出去）；</li>
 *   <li><b>落库</b>——条件更新 + 补写轨迹尾巴（{@link OrderRepository#update}）；</li>
 *   <li><b>回补库存</b>——跨服务写（store 域），放在最后：它跑失败时上面两步随事务一起回退，
 *       而它自己是**幂等**的（按流水净额归还，重复调用是 no-op），故重试安全。</li>
 * </ol>
 * <p>⚠ 反过来「先还库存再落库」会在落库失败时留下「库存已还、订单还是待支付」——库里没动过，
 * 顾客还能再付一次，而货架的库存已经多出来了。</p>
 *
 * <h3>这里只有本地事务，全局事务在调用方</h3>
 * <p>本类的 {@code @Transactional} 管的是**库里那一半**：{@link OrderRepository#update} 要写
 * 「状态列 + 状态轨迹」两处，一处失败另一处不得留下（列表按状态列、详情按轨迹重建，
 * 两者不合就是同一笔单在两个页面显示两个状态）。<br>
 * 而**跨服务的库存回补**需要的是全局事务（Seata TM 侧），那个注解**挂在调用方**
 * （{@code OrderApplicationService#cancelOrder} / {@code #refundOrder}，以及超时关单任务的入口）——
 * 与下单同一套分工：全局在**组件扫描出来的** {@code @Service} 上，本地在它调用的协作bean上。
 * ⚠ 原因是死的：{@code GlobalTransactionScanner} 按 {@code BeanDefinition.getBeanClassName()} 选目标，
 * 类名为空（{@code @Bean} 方法产出的 bean）即**静默跳过**。本类是组件扫描到的 {@code @Service}
 * （类名非空），故将来若把注解挪到这里也生效——但现在不放：一个动作入口只能有一个全局事务的开点。</p>
 *
 * <h3>并发与重复调用</h3>
 * <p>同一个动作被调用两次（双击、或顾客取消与超时关单撞在一起）：第二次在**第 1 步**就被状态机拒
 * （400「重复变更」），根本走不到落库与回补；若两步并发到「都读到旧状态」，则第 2 步的条件更新
 * 会有一个拿到 0 行 → 同样 400（见 {@link OrderRepository#update}）。⚠ 故**回补不会被执行两次**——
 * 即便真到了回补那一步，它也是幂等的，两道保险都不依赖「调用方记得自己调过没有」。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderCancelService {

    private final OrderRepository orderRepository;
    private final StockPort stockPort;
    private final OrderStatusFlow orderStatusFlow;

    /**
     * 取消这笔订单（**仅待支付可取消**）：状态推到「已取消」、落库、回补库存
     *
     * @param order 已按作用域读出来的订单（调用方负责查、本类负责改）
     * @throws com.panoramic.common.exception.ServiceException 当前状态不是待支付（HTTP 400，提示语可直接展示）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrderModel order) {
        order.markCancelled(orderStatusFlow);
        orderRepository.update(order);
        stockPort.revertByOrder(order.getOrderNo());
    }

    /**
     * 仅退款这笔订单（**仅「已支付、未发货」可退**，全额退）：状态推到「已退款」、落库、回补库存
     *
     * @param order 已按作用域读出来的订单（调用方负责查、本类负责改）
     * @throws com.panoramic.common.exception.ServiceException 当前状态不是已支付（HTTP 400，提示语可直接展示）
     */
    @Transactional(rollbackFor = Exception.class)
    public void refund(OrderModel order) {
        order.markRefunded(orderStatusFlow);
        orderRepository.update(order);
        stockPort.revertByOrder(order.getOrderNo());
    }
}
