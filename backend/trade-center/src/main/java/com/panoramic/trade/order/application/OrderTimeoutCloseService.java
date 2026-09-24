package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;

/**
 * 关掉一笔**超时未支付**的订单（域内入口，触发方是定时任务，不是 HTTP 调用）。
 *
 * <h3>它只是一层「取单」的壳，关单口径整个在 {@link OrderCancelService}</h3>
 * <p>本类做两件事：按单号取单（**不带作用域**，见下）、调用取消服务。状态闸门（必须仍是待支付）、
 * 落库、回补库存全部复用顾客主动取消那一份实现——超时关单与顾客点「取消」在域侧是**同一个动作**，
 * 差异只在「谁触发、凭什么判定超时」。各写一份的代价不是多几行，是「回补库存」变成两处实现。</p>
 *
 * <h3>为什么这里取单**不带作用域**</h3>
 * <p>作用域（{@code customerId} / {@code storeId}）的语义是「调用方被限定在谁的数据里」。
 * 关单任务不是某个顾客，它是域内系统级动作，合法视角就是全量——硬塞一个从订单里读出来的
 * customerId 进去，等于把「作用域」降级成一个自我实现的过滤器（每次都能查到，也永远挡不住任何东西），
 * 而那正是它唯一的作用。⚠ 这里**没有绕过任何闸门**：写侧必填作用域这条规矩管的是「外部调用方」
 * （MVC / Feign 进来的那些），域内系统动作本来就在这条线之外——取消服务里的状态机判定一点也不少。</p>
 *
 * <h3>为什么本类必须自己带 {@code @GlobalTransactional}</h3>
 * <p>关单要回补库存（写 store 域，跨服务），本地回滚补不回来。注解落在**本类**（组件扫描得到的
 * {@code @Service}）而不是调用方（{@link OrderTimeoutCloseTask}）——任务那边是「一批」的循环，
 * 一笔一个事务才符合现实：某一笔已经付过款（状态机拒）不该把这一批里已经关掉的那些一起回退。
 * ⚠ 与 {@link OrderCreateCoordinator} 那边的道理相同：Seata 按 {@code BeanDefinition.getBeanClassName()}
 * 挑要增强的 bean，类名恒空（{@code @Bean} 方法产出的 bean）即被**静默跳过**，故入口只能是扫描到的类。
 * ⚠ 而调用方必须是**另一个 bean**：本类若自己调自己，注解会连同代理一起被绕过（不再有任何事务）。</p>
 *
 * @see OrderCancelService 关单的三步（迁移 → 落库 → 回补）与顺序，全部在那里
 */
@Service
@RequiredArgsConstructor
public class OrderTimeoutCloseService {

    private final OrderRepository orderRepository;
    private final OrderCancelService orderCancelService;

    /**
     * 关掉这笔超时未支付的订单（幂等：已经不在待支付的单会被状态机拒 → 400）
     *
     * @param orderNo 业务可读单号
     * @throws ServiceException 订单不存在（HTTP 404）或当前状态不是待支付（HTTP 400，含「已被支付抢先」）
     */
    @GlobalTransactional
    public void close(String orderNo) {
        // 取单的口径与详情一致（同一条读路径），只是不收窄作用域
        OrderModel order = orderRepository.findOrder(OrderQuery.forDetail(orderNo, null, null))
                .orElseThrow(() -> new ServiceException(404, "订单不存在"));
        orderCancelService.cancel(order);
    }
}
