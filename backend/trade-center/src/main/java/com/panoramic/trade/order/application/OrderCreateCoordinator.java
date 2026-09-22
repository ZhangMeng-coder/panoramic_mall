package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderFingerprint;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderNoGenerator;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.OccupyResult;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.domain.port.StockPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 下单编排器：一次提交 → 占幂等键 → 按店铺拆成 N 笔订单 → 跑流水线 → 一次性落库，失败即整次回滚。
 *
 * <p>它承载 todo 里「商城端不管是从页面详情端直接下单还是从购物车点击结算，都直接生成订单」的全部编排，
 * 以及「需要考虑订单重复提交的问题」的两级幂等（裁定 D6）。它是唯一知道「一次提交由哪些笔组成」的地方。</p>
 *
 * <h3>为什么本地事务边界在这个方法上（裁定 D4 + 先占键）</h3>
 * <p>⚠ <b>全局事务 {@code @GlobalTransactional} 不在这里，在 {@link OrderApplicationService#create}</b>——
 * 那不是随手挪的：Seata 的 {@code GlobalTransactionScanner} 是按
 * <b>{@code BeanDefinition.getBeanClassName()}</b> 选目标的（类名为空即跳过），而本类由装配层的
 * {@code @Bean} 方法产出、<b>类名恒为空</b>，注解挂在这里会被**静默忽略**（不报错、不告警，事务根本不开）。
 * 详见 {@link OrderApplicationService#create} 的说明与它旁边的落点哨兵单测。</p>
 * <p>本地 {@code @Transactional} 留在这里，与全局事务**分工不同、并存而非互相替代**——全局事务（TM）管的是
 * **跨域的库存写入**（store 域是分支事务，回滚依据在它自己库里的 undo_log），本地事务管的是本地库的那一半
 * （先占键与订单一并落库）：去掉本地那行并不会被全局事务补上，「键与订单同事务」这条不变量本就是本地事务给的。</p>
 * <p>⚠ <b>本轮起它不再只是「将来的落点」，而是先占键语义的必要条件</b>：{@code occupy} 与 {@code saveAll}
 * 必须在**同一个事务**里，否则键会先落地——先到者随后业务失败时键残留，重放会回读到一个**空批次**；
 * 反之键与订单一并回滚，**失败不留残键**这件事就免费得到了，不需要任何清理补偿。</p>
 *
 * <h3>为什么库存扣减与订单写入不是逐笔提交（裁定 D13）</h3>
 * <p>全部步骤跑完、状态已是「待支付」、每笔都封存后，才 {@link OrderRepository#saveAll} 一次写进去
 * ——订单与「本次提交返回了哪一批」的关联**一并**落库。于是「失败不留残单」不需要靠删数据实现：
 * 失败时订单**从来没被写过**（库内写入随事务回滚），只剩「回补库存」要还——
 * 而库存在阶段一/二都是**外部资源**（内存脚手架 / store 域），不随本地事务回滚，必须显式归还。
 * 反过来若逐笔提交，中途失败就留下一批半成品残单，得再写一套补偿把它们删掉，那是第二条会出错的路。</p>
 *
 * <h3>两级幂等的键各自的作用域</h3>
 * <p>{@code requestId} 的作用域是**顾客内**：它由客户端生成，不同顾客之间不共享，
 * 故占键与回读都以 {@code (customerId, requestId)} 为键——只按键查会让 A 顾客的订单被 B 顾客
 * 用同一个键捞走（订单号 / 金额 / 门店全外泄）。第二级的指纹里本来就含顾客 id，天然不跨顾客。</p>
 *
 * <h3>为什么回补只针对「本次新建」的笔</h3>
 * <p>「复用」（指纹命中）的那一笔属于**上一次提交**，它的库存在上一次就已经扣过；
 * 对复用笔回补，等于把上次真实下单占用的库存还回货架——超卖。这条是「幂等复用」与
 * 「整次提交回滚」两条口径的交汇点，最容易写错（故有一条多店用例专门钉它）。</p>
 */
public class OrderCreateCoordinator {

    private final OrderRepository orderRepository;
    private final GoodsQueryPort goodsQueryPort;
    private final StockPort stockPort;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderCreatePipeline pipeline;
    private final OrderProperties properties;
    private final Clock clock;

    public OrderCreateCoordinator(OrderRepository orderRepository,
                                  GoodsQueryPort goodsQueryPort,
                                  StockPort stockPort,
                                  OrderNoGenerator orderNoGenerator,
                                  OrderCreatePipeline pipeline,
                                  OrderProperties properties,
                                  Clock clock) {
        this.orderRepository = orderRepository;
        this.goodsQueryPort = goodsQueryPort;
        this.stockPort = stockPort;
        this.orderNoGenerator = orderNoGenerator;
        this.pipeline = pipeline;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 创建订单（一次提交可能拆出多笔，一单一店）
     *
     * @param command 下单入参（已合并同 SKU 行）
     * @return **本次提交涉及的全部订单**（新建 + 复用的），按 {@code storeId} 升序排列；
     *         请求级幂等命中时返回的是**首次那次提交返回过的整批**（条数与订单号逐一致）
     * @throws ServiceException       商品不存在 / 不可购买 / 库存不足（HTTP 400，可原样透传页面）
     * @throws IllegalStateException  订单号连续冲突超过重试上限（生成器或环境出了问题）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderModel> create(OrderCreateCommand command) {
        Objects.requireNonNull(command, "下单入参不能为空");
        List<OrderLine> lines = toOrderLines(command.lines());

        // ① 先占键（第一级幂等）：往提交记录的 (customer_id, request_id) 唯一键上插一行。
        //    并发提交同一个请求时，后来者阻塞在唯一索引的行锁上；先到者提交 → 后来者拿到重复键，
        //    回读**先到者那一批**原样返回；先到者失败回滚 → 键随事务消失，后来者成为新的「第一个」。
        //    ⚠ 占键必须先于一切业务动作：放到后面就又变回「查 → 做 → 写」，并发双击会各下各的单。
        String requestId = normalizeRequestId(command.requestId());
        OccupyResult occupy = orderRepository.occupy(command.customerId(), requestId);
        if (!occupy.created()) {
            // 命中：连商品都不校验是刻意的——那批订单是在商品当时可买的前提下成立的，用此刻的商品状态去否掉它，
            // 只会让「重放同一个请求」比第一次更早失败；重放应当幂等，不该有副作用也不该有新判据。
            // ⚠ 返回的是**那次提交返回过的整批**（提交关联说了算），不是「按 requestId 查到的订单行」：
            // 一批里可能有指纹命中的复用笔，它们的 requestId 是上一次提交的，按行查会少返回几笔。
            return orderRepository.findBySubmissionId(occupy.submissionId());
        }

        // ② 取一次商品快照，**只为按 storeId 分组**（拆单的键在商品归属上，裁定 D3）；步骤链内会再各查一次
        Map<Long, SkuSnapshot> snapshots = goodsQueryPort.mapBySkuIds(lines.stream().map(OrderLine::skuId).toList());
        Map<Long, List<OrderLine>> linesByStore = groupByStore(lines, snapshots);

        // 整次提交共用一个下单时刻：一次请求里各笔的时间一致，测试与重放都可预测。
        // ⚠ **截到秒**：create_time 列是 DATETIME(0)，MySQL 会把小数秒**四舍五入**再存，
        //    于是「模型里的时刻」与「库里的时刻」最多差 1 秒；而 L2 窗口正是拿这一列与下面的 since
        //    比对（闭区间）——两边精度不同时，窗口最后一秒内同指纹的提交会被判成「窗口外 → 又下一单」，
        //    落库实现与内存实现的判定结果不一致。在同一处把基准落到秒，落库值、since、内存值才是同一个值。
        LocalDateTime createTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime since = createTime.minusSeconds(properties.getIdempotencyWindowSeconds());

        List<OrderModel> result = new ArrayList<>(linesByStore.size());   // 返回顺序 = 分组顺序（storeId 升序）
        List<OrderModel> created = new ArrayList<>(linesByStore.size());  // 本次新建（失败要回补的就是它们）
        Set<String> orderNosInBatch = new HashSet<>();
        try {
            for (Map.Entry<Long, List<OrderLine>> entry : linesByStore.entrySet()) {
                Long storeId = entry.getKey();
                List<OrderLine> storeLines = entry.getValue();

                // ③ 第二级幂等：拆单后**每笔**再按指纹在窗口内判重（裁定 D6）。窗口外的同指纹是新单——
                //    「同一顾客过一会儿又买同一批东西」是真实需求，不是重复提交。
                String fingerprint = OrderFingerprint.of(command.customerId(), command.source(), storeId, storeLines);
                Optional<OrderModel> reused = orderRepository.findRecentByFingerprint(fingerprint, since);
                if (reused.isPresent()) {
                    result.add(reused.get());
                    continue;
                }

                String orderNo = nextOrderNo(orderNosInBatch);
                orderNosInBatch.add(orderNo);
                OrderModel order = OrderModel.open(orderNo, command.customerId(), storeId,
                        storeNameOf(storeLines, snapshots), command.source(), command.address(),
                        requestId, fingerprint, createTime, storeLines);
                // ⚠ 入列必须在跑流水线**之前**：stock-check 可能已经扣了几行才失败，
                //    失败时这笔也得回补（它不在仓库里，但库存已经动了）
                created.add(order);
                pipeline.execute(order);
                // 状态在 open 时就是 PENDING_PAYMENT（statusTrail 初始即含它），这里不需要、也不该再迁移一次
                result.add(order);
            }
            // ④ 一次性落库（D13）：订单 + 明细 + 初始状态轨迹 + 本次提交的关联。
            //    **整批 result 都要进关联**（含复用笔）——重放这个请求时要返回的是「首次返回过的那一批」，
            //    少一笔就等于丢单。复用笔的 requestId 保持它原本的值，这里不改写。
            //    ⚠ 无条件调用（哪怕 created 为空）：一批全是复用笔时，关联本身仍必须记下来。
            orderRepository.saveAll(occupy.submissionId(), List.copyOf(result));
            return List.copyOf(result);
        } catch (RuntimeException e) {
            revertCreated(created, e);
            throw e;
        }
    }

    /**
     * 生成一个未被占用的单号（生成 → 查重 → 重试）
     *
     * <p>⚠ 查重要同时看**仓库**与**本批次**：拆单后同一批会生成多个单号，而它们此刻都还没入库，
     * 只查仓库会让同一批里拆出的两笔拿到同一个单号——那是落库时才炸、且炸在唯一键上的错。</p>
     *
     * @param orderNosInBatch 本批次已用掉的单号
     * @throws IllegalStateException 连续 {@code order-no-max-retry} 次都撞车（不无限重试）
     */
    private String nextOrderNo(Set<String> orderNosInBatch) {
        int maxRetry = properties.getOrderNoMaxRetry();
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            String candidate = orderNoGenerator.next();
            if (!orderNosInBatch.contains(candidate) && !orderRepository.existsByOrderNo(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("订单号连续生成 " + maxRetry + " 次都已被占用（panoramic.trade.order.order-no-max-retry="
                + maxRetry + "），请检查订单号生成器或调大重试上限");
    }

    /**
     * 按 storeId 分组（一单一店，裁定 D3），商品不存在即拒绝
     *
     * <p>⚠ 用 {@code TreeMap} 让分组结果**按 storeId 升序**：字典序本身没有业务含义，
     * 但它给了「同一批入参 → 同一个返回顺序」这条确定性——测试可断言、重放可对账、
     * 日志里同一批订单的排列也稳定。</p>
     *
     * @throws ServiceException 有 skuId 查不到商品（HTTP 400）
     */
    private Map<Long, List<OrderLine>> groupByStore(List<OrderLine> lines, Map<Long, SkuSnapshot> snapshots) {
        Map<Long, List<OrderLine>> grouped = new TreeMap<>();
        for (OrderLine line : lines) {
            SkuSnapshot snapshot = snapshots.get(line.skuId());
            if (snapshot == null) {
                // 拆单阶段就报「不存在」：连归属店铺都不知道，这笔订单根本无从建起
                throw new ServiceException(400, "商品不存在");
            }
            grouped.computeIfAbsent(snapshot.storeId(), storeId -> new ArrayList<>()).add(line);
        }
        return grouped;
    }

    /**
     * 取该店的店铺名（一单一店 → 同组各行的店铺名必然相同，任取一行即可）
     *
     * <p>为什么由编排层填而不是让步骤填：见 {@code OrderModel#open} 的参数说明——
     * 一单一店下店铺名是那一组的一个常量，让步骤去填会引入「多行谁说了算」的歧义。</p>
     */
    private String storeNameOf(List<OrderLine> storeLines, Map<Long, SkuSnapshot> snapshots) {
        return snapshots.get(storeLines.get(0).skuId()).storeName();
    }

    /**
     * 回滚本次新建的笔：**逆序**逐单回补库存
     *
     * <p>⚠ 只补 {@code created}（本次新建）而**不含复用笔**：复用笔的库存在上一次提交就扣过了，
     * 对它回补等于把上次真实下单占用的库存还回货架——超卖。这条是「幂等复用」与「整次提交回滚」
     * 的交汇点，也有一条多店用例专门钉它。</p>
     *
     * <p>⚠ 回补的是**外部资源**（阶段一是内存脚手架、阶段二是 store 域），它不随本地事务回滚，
     * 故必须在这里显式归还；而库内的订单写入会随事务一起消失，不需要也不该去删。这正是
     * 「失败不留残单 + 库存要还」两件事的分工。</p>
     *
     * <p>⚠ 逆序是刻意的：正序扣、逆序还，回补顺序与库存被占用的顺序相反，
     * 将来接入真实库存实现时，这一顺序与事务的回滚顺序一致。回补本身**按单幂等**
     * （{@link StockPort#revertByOrder} 按流水净额归还），故重复进入同一个失败 episode 也不会多还。</p>
     *
     * <p>⚠ 本方法**不吞主异常**：回补过程中的次生错误挂到 {@code addSuppressed} 上，
     * 抛出去的仍是原始异常（原始类型与消息必须原样到达 BFF——4xx 要原样透传给页面，5xx 要计入熔断）。</p>
     *
     * @param created 本次新建的订单（不含复用的笔）
     * @param primary 触发回滚的原始异常
     */
    private void revertCreated(List<OrderModel> created, RuntimeException primary) {
        for (int i = created.size() - 1; i >= 0; i--) {
            try {
                stockPort.revertByOrder(created.get(i).getOrderNo());
            } catch (RuntimeException secondary) {
                primary.addSuppressed(secondary);
            }
        }
    }

    /**
     * 空白字符串视为「没传」：调用方（BFF）在没拿到幂等键时往往塞一个空串而不是 null，
     * 若把空串当有效键，第一笔 requestId 为空串的订单会把后面所有同样为空的提交都顶掉。
     */
    private static String normalizeRequestId(String requestId) {
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    /**
     * 把 application 层的行翻译成 domain 的行
     *
     * <p>两个 record 刻意不合并（裁定 D11：domain 不引 application），翻译点就固定在本类这一处。</p>
     */
    private static List<OrderLine> toOrderLines(List<OrderCreateCommand.Line> lines) {
        return lines.stream().map(line -> new OrderLine(line.skuId(), line.quantity())).toList();
    }
}
