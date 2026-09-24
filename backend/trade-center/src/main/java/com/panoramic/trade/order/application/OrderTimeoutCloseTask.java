package com.panoramic.trade.order.application;

import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时未支付自动关单：按固定节奏扫一批**已过支付截止时刻、仍停在待支付**的单，逐笔关掉
 * （todo：「后台需要增加订单取消定时任务，若有超时未支付订单，直接取消」）。
 *
 * <h3>职责边界：取数 + 触发，判定与关单都不在这里</h3>
 * <p>本类只做三件事：问仓储要一批超时单（条件见 {@link OrderRepository#findTimeoutPending}）、
 * 逐笔调关单入口（{@link OrderTimeoutCloseService#close}）、把单笔失败**吞掉并记账**。
 * 「什么算超时」（{@code OrderModel#isTimedOut} / SQL 里的三个条件）、「关单要做什么」
 * （迁移 + 落库 + 回补库存）各在各自那一处，本类不重写任何一条。</p>
 *
 * <h3>为什么一笔一个事务，且某笔失败不影响同批其它笔</h3>
 * <p>关单入口自己带 {@code @GlobalTransactional}（回补库存是跨服务写），故循环里每次调用都是一个
 * <b>独立事务</b>。这是刻意的：一批里最可能失败的那一笔恰恰是「刚刚被顾客付掉」的单——状态机拒它
 * （400）。若整批共用一个事务，这一笔就会把同批里已经关掉的单一起回退，于是下一轮又从头关一遍，
 * 只要有活跃顾客在付款就永远关不干净。故：**单笔失败只记一行日志，继续关下一笔**。</p>
 *
 * <h3>幂等与并发</h3>
 * <p>任务本身幂等：关过的单不再满足「仍停在待支付」，下一轮捞不到它。与顾客主动取消撞车时，
 * 两边都走同一份取消服务——先到的赢，后到的在状态机上被拒（400），被本类吞掉记日志；
 * 而落库那一步还是**条件更新**，真并发也只会有一个拿到 1 行。故重复扫描不会重复关单、
 * 更不会重复回补库存（回补本身也幂等，见 {@code StockPort#revertByOrder}）。</p>
 *
 * <h3>两个可调项各自从哪来</h3>
 * <p>扫描节奏走 {@code @Scheduled} 的占位符（{@code panoramic.trade.order.timeout-close-interval-ms}，
 * 默认 60s）——注解只能吃一个字符串表达式，绕经配置类反而多一层转手；
 * 单批上限走 {@link OrderProperties}（它有装配期断言）。<b>两者都不是业务口径</b>：
 * 真正的口径是「支付时限」（{@code payment-timeout-minutes}），扫描节奏只决定「最晚晚多久关掉」。</p>
 *
 * <p>⚠ {@code fixedDelay} 而不是 {@code fixedRate}：后者按「上一轮开始时刻」排下一次，
 * 一批关得久了会造成两轮扫描重叠（同一批单被两个线程同时关），徒增无谓的竞争与日志噪音。</p>
 */
@Slf4j
@Component
public class OrderTimeoutCloseTask {

    private final OrderRepository orderRepository;
    private final OrderTimeoutCloseService orderTimeoutCloseService;
    private final Clock clock;

    /** 单批上限（来自配置，装配期断言为正数） */
    private final int batchSize;

    public OrderTimeoutCloseTask(OrderRepository orderRepository,
                                OrderTimeoutCloseService orderTimeoutCloseService,
                                OrderProperties properties,
                                Clock clock) {
        this.orderRepository = orderRepository;
        this.orderTimeoutCloseService = orderTimeoutCloseService;
        this.clock = clock;
        this.batchSize = properties.getTimeoutCloseBatchSize();
        // 上限必须为正：配成 0 或负数会让每一轮都捞不到任何单（任务看着在跑、其实一笔都不关），
        // 且不报任何错。配置错是程序员错误，装配期就让它起不来（同 OrderCreateCoordinator 的时限断言）
        if (batchSize <= 0) {
            throw new IllegalStateException("超时关单的单批上限必须是正数（panoramic.trade.order.timeout-close-batch-size="
                    + batchSize + "），否则每一轮都捞不到单、任务静默失效");
        }
    }

    /**
     * 扫一轮：捞一批超时未支付的单，逐笔关掉
     *
     * <p>⚠ 时刻从 {@code Clock} 取（不取系统时间）：与 {@code create_time} / {@code expire_time}
     * 同一口径，且单测能钉住边界那一秒。</p>
     */
    @Scheduled(fixedDelayString = "${panoramic.trade.order.timeout-close-interval-ms:60000}")
    public void closeTimeoutOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<OrderModel> timeoutOrders = orderRepository.findTimeoutPending(now, batchSize);
        if (timeoutOrders.isEmpty()) {
            // 常态：一分钟一次、绝大多数轮次无事可做，故不打日志（否则日志里全是「本轮 0 笔」）
            return;
        }
        int closed = 0;
        for (OrderModel order : timeoutOrders) {
            try {
                orderTimeoutCloseService.close(order.getOrderNo());
                closed++;
            } catch (RuntimeException e) {
                // 最典型的一笔：顾客在本轮捞数之后、关单之前把钱付了 → 状态机拒（400「订单状态不能从
                // 「已支付」变更为「已取消」」）。这类单**本来就不该被关**，记一行日志、继续下一笔。
                // ⚠ 单笔失败绝不能中断整批：那就是「只要有顾客在付款，超时单永远关不干净」。
                log.warn("超时关单失败，跳过这一笔：orderNo={}，原因={}", order.getOrderNo(), e.getMessage());
            }
        }
        // 关掉的笔数记一行：任务静默失效（一笔都关不掉）时，只有日志能把这件事说出来
        log.info("超时关单：本轮捞到 {} 笔，已关闭 {} 笔", timeoutOrders.size(), closed);
    }
}
