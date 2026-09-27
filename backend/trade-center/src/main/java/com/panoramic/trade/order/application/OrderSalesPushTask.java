package com.panoramic.trade.order.application;

import com.panoramic.trade.order.application.config.SalesPushProperties;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 销量补推：按固定节奏扫一批**已完成、但销量还没推给 store 域**的单，逐笔补推
 * （收货后的快路径失败时唯一的出路，见 {@link OrderSalesPushListener}）。
 *
 * <h3>职责边界：取数 + 触发，推送与判定都不在这里</h3>
 * <p>本类只做三件事：问仓储要一批未推送的已完成单（条件见
 * {@code OrderRepository#findReceivedUnpushed}）、逐笔调 {@link OrderSalesPushService#push}、
 * 把单笔失败**按两类分别吞掉并记账**。「什么算已完成且未推送」「推什么字段」各在各自那一处，
 * 本类不重写任何一条——⚠ 尤其是**不能**在这里自己拼 DTO 或自己判状态：
 * 那样兜底就成了第二份推送口径，与快路径分岔时不会有任何东西报错。</p>
 *
 * <h3>为什么一笔一个事务，且某笔失败不影响同批其它笔</h3>
 * <p>推送服务本身不带事务（读 → 推 → 标记三步各自成事，见 {@link OrderSalesPushService}），
 * 故循环里每次调用天然互不影响。这是刻意的：一笔单推不动（对方的某个 SPU 数据有问题、
 * 序列化失败）不该让同批其它笔也一起推不出去——否则积压里只要有一笔「毒药单」，
 * 整个补推就永远停滞在它身上。</p>
 *
 * <h3>幂等与并发</h3>
 * <p>任务本身幂等：推成功即置 {@code sales_pushed = 1}，置了就不再满足「未推送」，下一轮捞不到它。
 * 与快路径（{@code OrderSalesPushListener}）撞车时，两边推的是同一笔单的同一批 SPU 件数——
 * store 域的台账唯一键 {@code (order_no, spu_id)} 把重复吸收成 no-op 成功，故**重复推不会错账**。
 * 两边也都可能去置同一个标记，写 1 写两次无害。</p>
 *
 * <h3>单笔失败分两类，不能混进一个桶</h3>
 * <p>「跳过」（{@code push} 返回 false：订单不是已完成态）与「失败」（{@code RuntimeException}：
 * Feign 不通、store 侧业务拒绝、数据被写坏）是两件事——后者是故障（要带堆栈、汇总行升到 error），
 * 前者不是。⚠ 本任务的「跳过」在正常路径上**恒为 0**：捞数的 SQL 已经按 {@code status = RECEIVED}
 * 收窄，而推送前那道校验读的是同一个对象。故它一旦非 0，说明「捞数条件」与「推送前的校验」
 * 被改岔了（例如有人把 SQL 里的状态条件去掉），而这类分岔不报错、只表现成
 * 「每轮都捞到 N 笔、却一笔都不推」——三个计数分开就是为了让它现形。</p>
 *
 * <h3>两个可调项各自从哪来</h3>
 * <p>扫描节奏走 {@code @Scheduled} 的占位符（{@code panoramic.trade.order.sales-push.interval-ms}，
 * 默认 60s）——注解只能吃一个字符串表达式，绕经配置类反而多一层转手；
 * 单批上限走 {@link SalesPushProperties}（它有装配期断言）。<b>两者都不是业务口径</b>：
 * 口径是「销量只记订单完成的件数」，扫描节奏只决定「最晚晚多久补上」。</p>
 *
 * <p>⚠ {@code fixedDelay} 而不是 {@code fixedRate}：后者按「上一轮开始时刻」排下一次，
 * 一批推得久了会造成两轮扫描重叠（同一批单被两个线程同时推），徒增无谓的竞争与日志噪音。
 * ⚠ 本任务与 {@code OrderTimeoutCloseTask} 共用默认的调度线程池（本域只有一个线程）：
 * 两轮扫描因此**串行**、互不重叠。对两个都是兜底任务来说这是可接受的；
 * 代价是某一轮扫得很久时另一轮会推迟——但收货后的快路径不受影响（它走自己的线程池）。</p>
 */
@Slf4j
@Component
public class OrderSalesPushTask {

    /**
     * 末尾汇总那一行（**唯一一份措辞**：两个级别共用一个格式串，故写成常量而不是两处字面量）。
     *
     * <p>⚠ 三个计数缺一不可：「推送 0 笔」既可能是「本轮根本没有积压」（正常，但那种情况
     * 一轮都不会记日志——捞不到就提前返回了），也可能是「域侧一直在拒」（可疑）或「系统全挂」（故障）。</p>
     */
    private static final String SUMMARY =
            "销量补推：本轮捞到 {} 笔，推送 {} 笔，跳过 {} 笔（不是已完成态），失败 {} 笔（系统错）";

    private final OrderRepository orderRepository;
    private final OrderSalesPushService orderSalesPushService;

    /** 单批上限（来自配置，装配期断言为正数） */
    private final int batchSize;

    public OrderSalesPushTask(OrderRepository orderRepository,
                              OrderSalesPushService orderSalesPushService,
                              SalesPushProperties properties) {
        this.orderRepository = orderRepository;
        this.orderSalesPushService = orderSalesPushService;
        this.batchSize = properties.getBatchSize();
        // 上限必须为正：配成 0 或负数会让每一轮都捞不到任何单（任务看着在跑、其实一笔都不推），
        // 且不报任何错。配置错是程序员错误，装配期就让它起不来（同 OrderTimeoutCloseTask 的断言）
        if (batchSize <= 0) {
            throw new IllegalStateException("销量补推的单批上限必须是正数（panoramic.trade.order.sales-push.batch-size="
                    + batchSize + "），否则每一轮都捞不到单、任务静默失效");
        }
    }

    /**
     * 扫一轮：捞一批未推送的已完成单，逐笔补推
     *
     * <p>⚠ 不需要时刻参数（与超时关单任务不同）：销量没有时间窗——「哪一笔还没推」由
     * {@code sales_pushed} 那一列回答，与当前时刻无关。</p>
     */
    @Scheduled(fixedDelayString = "${panoramic.trade.order.sales-push.interval-ms:60000}")
    public void pushReceivedSales() {
        List<OrderModel> pending = orderRepository.findReceivedUnpushed(batchSize);
        if (pending.isEmpty()) {
            // 常态：一分钟一次、绝大多数轮次无事可做（推送在收货时就成功了），故不打日志
            return;
        }
        int pushed = 0;
        int skipped = 0;
        int failed = 0;
        for (OrderModel order : pending) {
            try {
                if (orderSalesPushService.push(order)) {
                    pushed++;
                } else {
                    // 正常路径到不了（捞数已按已完成收窄）。走到这里即「捞数条件」与「推送前的校验」
                    // 分岔了——只记一行（不带堆栈）：它不是故障，是代码改岔了，靠汇总行现形
                    skipped++;
                    log.warn("销量补推跳过（不是已完成态）：orderNo={}", order.getOrderNo());
                }
            } catch (RuntimeException e) {
                // 系统错（Feign 不通 / store 侧拒绝 / 数据被写坏）：这一笔会一直停在未推送，
                // 且光看 message 分不出是哪一类——故带堆栈记 error（末尾汇总也升到 error）。
                // 不加重试：标记没被置上，下一轮扫描天然会重捞它，恢复后自动推上去
                failed++;
                log.error("销量补推失败（下一轮会重捞）：orderNo={}", order.getOrderNo(), e);
            }
        }
        // 记一行汇总：任务静默失效（一笔都推不出去）时，只有日志能把这件事说出来
        if (failed > 0) {
            log.error(SUMMARY, pending.size(), pushed, skipped, failed);
        } else {
            log.info(SUMMARY, pending.size(), pushed, skipped, failed);
        }
    }
}
