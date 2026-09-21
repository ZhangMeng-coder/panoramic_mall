package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.config.OrderProperties;
import com.panoramic.trade.order.domain.OrderFingerprint;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderLine;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderNoGenerator;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.domain.port.OrderSubmission;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.domain.port.StockOutboundRecord;
import com.panoramic.trade.order.domain.port.StockPort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 下单编排器：一次提交 → 按店铺拆成 N 笔订单 → 两级幂等 → 跑流水线 → 一次性落库，失败即整次回滚。
 *
 * <p>它承载 todo 里「商城端不管是从页面详情端直接下单还是从购物车点击结算，都直接生成订单」的全部编排，
 * 以及「需要考虑订单重复提交的问题」的两级幂等（裁定 D6）。它是唯一知道「一次提交由哪些笔组成」的地方。</p>
 *
 * <h3>为什么事务边界在这里（裁定 D4）</h3>
 * <p>todo 要求「订单创建过程中使用 seata 保证分布式事务」，本期**不引依赖、不加注解**，
 * 但把边界先立在这里：将来接 Seata 的落点 = **本方法入口加 {@code @GlobalTransactional}**，
 * 库存的真实写入方在 store 域（本域只经 {@code StockPort} 调它）。本类现在做的事，
 * 正是那个全局事务要保证的事——任一笔失败，整次提交不留痕：库存回补 + 追加反向出库记录 + 不落单。</p>
 *
 * <h3>为什么库存扣减与订单写入不是逐笔提交（裁定 D13）</h3>
 * <p>全部步骤跑完、状态已是「待支付」、每笔都封存后，才 {@link OrderRepository#saveSubmission} 一次写进去
 * ——订单与「本次提交返回了哪一批」的映射**一并**落库（两者必须原子，理由见该方法的注释）。
 * 于是「失败不留残单」这件事不需要靠删数据实现——失败时订单**从来没被写过**，只剩「回补库存」要还。
 * 反过来若逐笔提交，中途失败就留下一批半成品残单，得再写一套补偿把它们删掉，那是第二条会出错的路。</p>
 *
 * <p>⚠ 落库后若两者在真实实现里不是原子的（跨表 / 跨库），第一级幂等会**自愈着退化**：订单在、映射丢了时，
 * 重放会走第二级指纹复用——窗口内各笔都还能命中，仍返回同一批；但窗口外就没有任何东西挡得住它，
 * 会真的再下一单。故「退化」只在窗口内无害，映射本身不是缓存、不能省。</p>
 *
 * <h3>两级幂等的键各自的作用域</h3>
 * <p>{@code requestId} 的作用域是**顾客内**：它由客户端生成，不同顾客之间不共享，
 * 故第一级查询必须连 {@code customerId} 一起收窄——只按键查会让 A 顾客的订单被 B 顾客用同一个键捞走
 * （订单号 / 金额 / 门店全外泄）。第二级的指纹里本来就含顾客 id，天然不跨顾客。</p>
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
     * @return **本次提交涉及的全部订单**（新建 + 复用的），按 `storeId` 升序排列；
     *         {@code requestId} 命中时返回的是**首次那次提交返回过的整批**（条数与订单号逐一致）
     * @throws ServiceException       商品不存在 / 不可购买 / 库存不足（HTTP 400，可原样透传页面）
     * @throws IllegalStateException  订单号连续冲突超过重试上限（生成器或环境出了问题）
     */
    public List<OrderModel> create(OrderCreateCommand command) {
        Objects.requireNonNull(command, "下单入参不能为空");
        List<OrderLine> lines = toOrderLines(command.lines());

        // ① 第一级幂等：requestId 命中就整批原样返回——不重建、不再扣库存、不校验商品。
        //    连商品都不校验是刻意的：那批订单是在商品当时可买的前提下成立的，用此刻的商品状态去否掉它，
        //    只会让「重放同一个请求」比第一次更早失败——重放应当幂等，不该有副作用也不该有新判据。
        //    ⚠ 返回的是**那次提交返回过的整批**（提交记录说了算），不是「按 requestId 查到的订单行」：
        //    一批里可能有指纹命中的复用笔，它们的 requestId 是上一次提交的，按行查会少返回几笔。
        //    ⚠ 查询按 customerId 收窄：requestId 的作用域是顾客内，否则 B 顾客能拿 A 的键捞走 A 的订单。
        String requestId = normalizeRequestId(command.requestId());
        if (requestId != null) {
            Optional<OrderSubmission> previous = orderRepository.findSubmission(command.customerId(), requestId);
            if (previous.isPresent()) {
                return previous.get().orders();
            }
        }

        // ② 取一次商品快照，**只为按 storeId 分组**（拆单的键在商品归属上，裁定 D3）；步骤链内会再各查一次
        Map<Long, SkuSnapshot> snapshots = goodsQueryPort.mapBySkuIds(lines.stream().map(OrderLine::skuId).toList());
        Map<Long, List<OrderLine>> linesByStore = groupByStore(lines, snapshots);

        // 整次提交共用一个下单时刻：一次请求里各笔的时间一致，测试与重放都可预测
        LocalDateTime createTime = LocalDateTime.now(clock);
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
                Optional<OrderModel> reused = orderRepository.findByFingerprint(fingerprint, since);
                if (reused.isPresent()) {
                    result.add(reused.get());
                    continue;
                }

                String orderNo = nextOrderNo(orderNosInBatch);
                orderNosInBatch.add(orderNo);
                OrderModel order = OrderModel.open(orderNo, command.customerId(), storeId,
                        storeNameOf(storeLines, snapshots), command.source(), requestId, fingerprint, createTime, storeLines);
                // ⚠ 入列必须在跑流水线**之前**：stock-check 可能已经扣了几行才失败，
                //    失败时这笔也得回补（它不在仓库里，但库存已经动了）
                created.add(order);
                pipeline.execute(order);
                // 状态在 open 时就是 PENDING_PAYMENT（statusTrail 初始即含它），这里不需要、也不该再迁移一次
                result.add(order);
            }
            // ④ 一次性落库（D13）：订单 + 本次提交的幂等映射。**整批 result 都要进映射**（含复用笔）——
            //    重放这个请求时要返回的是「首次返回过的那一批」，少一笔就等于丢单。
            //    复用笔的 requestId 保持它原本的值，这里不改写：它记的是「哪次提交创造了这笔单」。
            //    ⚠ 无条件调用（哪怕 created 为空）：一批全是复用笔时，映射本身仍必须记下来。
            orderRepository.saveSubmission(
                    new OrderSubmission(requestId, command.customerId(), List.copyOf(result)));
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
     * 回滚本次新建的笔：**逆序**逐行回补库存
     *
     * <p>⚠ 回补前先看一遍出库流水，只还「确实扣过的行」——<b>这一段是本层唯一的回补幂等保证</b>：
     * {@link StockPort#revert} 不做任何去重（调一次就还一次），既不能靠它挡住重复回补，
     * 也不能靠它判断「这笔到底扣没扣」。两个错误方向都由净额算式兜住：</p>
     * <ul>
     *   <li><b>别还多</b>：失败可能发生在 stock-check **之前或之中**——那时这一行根本没扣，
     *       盲目回补会把库存**冲多**（超卖的镜像错误：不报错、只写坏数据）。净额 {@code <= 0} 即跳过。</li>
     *   <li><b>别漏还</b>：失败提交不落库 → 同一单号可能被两次失败提交复用 → 若端口那边再按
     *       {@code orderNo + skuId} 去重，第二次**真实**回补会被静默吃掉（库存永久少扣、流水却显示净 0）。
     *       故去重只发生在这一层，且判据是「流水净额」而不是「我是不是调过」。</li>
     * </ul>
     * <p>净额算法天然自愈：本方法若被重复进入（同一失败 episode 第二次），流水里已有一条负记录，
     * 净额归零 → 自然跳过。真实实现下这一遍可换成「按订单号查出库记录」的窄查询。</p>
     *
     * <p>⚠ 逆序是刻意的：正序扣、逆序还，回补顺序与库存被占用的顺序相反，
     * 未来接入「按订单回补」的真实库存实现时，这一顺序与事务的回滚顺序一致。</p>
     *
     * <p>⚠ 本方法**不吞主异常**：回补过程中的次生错误挂到 {@code addSuppressed} 上，
     * 抛出去的仍是原始异常（原始类型与消息必须原样到达 BFF——4xx 要原样透传给页面，5xx 要计入熔断）。</p>
     *
     * @param created 本次新建的订单（不含复用的笔）
     * @param primary 触发回滚的原始异常
     */
    private void revertCreated(List<OrderModel> created, RuntimeException primary) {
        if (created.isEmpty()) {
            return;
        }
        Map<String, Integer> pending = pendingDeductions();
        for (int i = created.size() - 1; i >= 0; i--) {
            OrderModel order = created.get(i);
            for (OrderItem item : order.getItems()) {
                Integer deducted = pending.get(deductionKey(order.getOrderNo(), item.getSkuId()));
                if (deducted == null || deducted <= 0) {
                    continue;
                }
                try {
                    stockPort.revert(item.getSkuId(), deducted, order.getOrderNo());
                } catch (RuntimeException secondary) {
                    primary.addSuppressed(secondary);
                }
            }
        }
    }

    /**
     * 汇总「订单号 + skuId → 净出库量」，作为回补的判据与数量来源
     *
     * <p>取净量而不是逐条记录：同一 {@code orderNo + skuId} 若已有过回补，净量自然变小/归零，
     * 于是「回补已经发生过」与「这一行压根没扣过」用同一套算式就都覆盖了。</p>
     */
    private Map<String, Integer> pendingDeductions() {
        Map<String, Integer> net = new HashMap<>();
        for (StockOutboundRecord record : stockPort.outboundRecords()) {
            if (record.orderNo() == null || record.skuId() == null) {
                continue;
            }
            net.merge(deductionKey(record.orderNo(), record.skuId()), record.quantity(), Integer::sum);
        }
        return net;
    }

    private static String deductionKey(String orderNo, Long skuId) {
        return orderNo + "|" + skuId;
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
