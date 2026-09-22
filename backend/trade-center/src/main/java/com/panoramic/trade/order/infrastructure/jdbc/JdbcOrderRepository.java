package com.panoramic.trade.order.infrastructure.jdbc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.OccupyResult;
import com.panoramic.trade.order.domain.port.OrderPage;
import com.panoramic.trade.order.domain.port.OrderPageQuery;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.order.infrastructure.entity.TradeOrder;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderItem;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderStatusLog;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmission;
import com.panoramic.trade.order.infrastructure.entity.TradeOrderSubmissionOrder;
import com.panoramic.trade.order.infrastructure.service.TradeOrderItemService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderStatusLogService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderSubmissionOrderService;
import com.panoramic.trade.order.infrastructure.service.TradeOrderSubmissionService;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link OrderRepository} 的落库实现（真实适配器）：5 张表 + MyBatis-Plus 基类，**不写一句自定义 SQL**。
 *
 * <h3>为什么不需要自定义 mapper 方法</h3>
 * <p>本类要的四类动作，恰好都在 {@code IService} 的能力里：插一行（{@code save}）、按条件取一批
 * （{@code list} / {@code page}）、判存在（{@code exists}）、改一列（{@code updateById}）。
 * 唯一「非基类」的地方是 {@link #occupy} 的重复键捕获与回查——那也不是 SQL，是异常语义。
 * 少一层手写 SQL，就少一层「列名写错了但编译通过」的地方。</p>
 *
 * <h3>并发口径：靠唯一索引的行锁，不靠应用层判断</h3>
 * <p>{@link #occupy} 往 {@code trade_order_submission} 插一行，其 {@code (customer_id, request_id)}
 * 唯一键就是 L1 幂等键。并发重复提交由数据库串行化：后到者**阻塞**在唯一索引的行锁上，
 * 先到者提交则后到者拿到重复键（→ 回查先到者的 id，回读那一批），先到者回滚则后到者插入成功。
 * ⚠ 前提是调用方（{@code OrderCreateCoordinator#create}）持有事务且覆盖到 {@link #saveAll}——
 * 本类**不自己开事务**，也不该开：键与订单必须同生共死，事务边界只能在编排层。</p>
 *
 * <p>⚠ 为什么捕获 {@link DataIntegrityViolationException}（而不是更精确的 {@code DuplicateKeyException}）：
 * 1062 由 mybatis-spring 的异常翻译器翻成 {@code DuplicateKeyException}，而它只是前者的子类；
 * 捕父类是为了不因翻译器换实现而漏捕。捕到之后一律**回查**：查得到先到者 = 幂等命中；
 * 查不到 = 撞的是别的约束（外键 / 非空），原样抛出，绝不把「别的约束错了」当成幂等命中吞掉。</p>
 *
 * <h3>写入：审计字段与时间列的边界</h3>
 * <p>{@code create_user/update_user/update_time} 全部交给 {@code MyMetaObjectHandler} 自动填充，本类不赋值
 * （CLAUDE.md 的硬规则）。<b>唯一显式赋值的是时间列</b>：{@code trade_order.create_time} 必须等于编排层
 * 算出的下单时刻（L2 幂等窗口以它为基准），{@code trade_order_status_log.create_time} 必须等于
 * 状态变更时刻（它就是「发生时刻」，见 DDL 注释）。自动填充只在字段为 null 时生效，故显式赋值即胜出。</p>
 *
 * <h3>读取：读出来的东西也要能自证</h3>
 * <p>库里读到的数据也可能是坏的（列串位、轨迹被截断、枚举名被改成不认识的值），这类错误不在这里挡住，
 * 就会一路带到页面上。故 {@link #assemble} 与 {@link #toOrderModel} 全程对账：
 * 枚举名能解析、明细小计与单价×数量一致（{@code OrderItem.rehydrate} 内）、轨迹 {@code seq} 连续且是
 * 合法的「下标 +1」路径（{@code OrderModel.rehydrate} 内）、落库的总件数/总金额与按行重算的一致。
 * ⚠ 这些一律抛 {@link IllegalStateException}（数据被写坏），**不是** {@code ServiceException(400)}——
 * 后者会被当作业务错误原样透传给页面，把「库里的数据坏了」说成「你的操作不对」。</p>
 *
 * <p>⚠ 分页按 {@code id DESC}（= 下单倒序，最新在前）：与内存实现同口径，页面的「我的订单」
 * 第一眼看到的就是刚下的那笔。</p>
 */
public class JdbcOrderRepository implements OrderRepository {

    private final TradeOrderSubmissionService submissionService;
    private final TradeOrderSubmissionOrderService submissionOrderService;
    private final TradeOrderService orderService;
    private final TradeOrderItemService itemService;
    private final TradeOrderStatusLogService statusLogService;
    private final OrderStatusFlow statusFlow;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcOrderRepository(TradeOrderSubmissionService submissionService,
                              TradeOrderSubmissionOrderService submissionOrderService,
                              TradeOrderService orderService,
                              TradeOrderItemService itemService,
                              TradeOrderStatusLogService statusLogService,
                              OrderStatusFlow statusFlow,
                              ObjectMapper objectMapper,
                              Clock clock) {
        this.submissionService = submissionService;
        this.submissionOrderService = submissionOrderService;
        this.orderService = orderService;
        this.itemService = itemService;
        this.statusLogService = statusLogService;
        this.statusFlow = statusFlow;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ── 写侧 ────────────────────────────────────────────────────────────────────

    @Override
    public OccupyResult occupy(Long customerId, String requestId) {
        TradeOrderSubmission row = new TradeOrderSubmission();
        row.setCustomerId(customerId);
        row.setRequestId(requestId);
        try {
            submissionService.save(row);
            return new OccupyResult(row.getId(), true);
        } catch (DataIntegrityViolationException e) {
            TradeOrderSubmission winner = submissionService.getOne(Wrappers.<TradeOrderSubmission>lambdaQuery()
                    .eq(TradeOrderSubmission::getCustomerId, customerId)
                    .eq(TradeOrderSubmission::getRequestId, requestId));
            if (winner == null) {
                // 撞的不是这把幂等键（外键 / 非空 / …），不能当成幂等命中吞掉
                throw e;
            }
            return new OccupyResult(winner.getId(), false);
        }
    }

    @Override
    public void saveAll(long submissionId, List<OrderModel> orders) {
        for (OrderModel order : orders) {
            if (!existsByOrderNo(order.getOrderNo())) {
                insertOrder(order);
            }
            // ⚠ 关联行**无条件写**：复用笔（指纹命中，已在库里）也要进本次提交的成员关系，
            //    否则重放这一次提交会少返回几笔（成员关系才是重放的依据，见 OrderRepository 的接口注释）
            TradeOrderSubmissionOrder link = new TradeOrderSubmissionOrder();
            link.setSubmissionId(submissionId);
            link.setOrderNo(order.getOrderNo());
            submissionOrderService.save(link);
        }
    }

    @Override
    public void update(OrderModel order) {
        // ⚠ **条件更新**，不是按主键盲写：库里必须仍处在「来时状态」，否则本次写入就是一次**丢失更新**。
        //    它挡的是两个动作同时作用在同一笔单上（双击「发货」、或「店主发货 × 顾客收货」）：
        //    两边各自读到旧状态、又都过了各自模型上的状态机，于是
        //    ① 盲写会让后到者把 status 列**推回旧值**（读到「已支付」、库里已被推到「已收货」，一次 ship 写回
        //       「已发货」），而轨迹按 (order_no, seq) 唯一键又插不进去 → status 列与轨迹互不一致；
        //       列表页按 status 列筛、详情页按轨迹重建 → **同一笔单在两个页面显示两个状态**；
        //    ② 同动作并发时第二条轨迹撞唯一键 → DuplicateKeyException → 500，而 R8 承诺的是 400，
        //       且 5xx 会计入端 BFF 的熔断失败率。
        //    条件更新把并发收敛成「一个赢、另一个 0 行」，0 行走下面那条 400 —— 轨迹插入因此不可能再撞车
        //    （只有赢了条件更新的那个事务会走到插入），故不需要再去 catch 重复键。
        OrderStatus expected = previousStatus(order);
        boolean updated = orderService.lambdaUpdate()
                .eq(TradeOrder::getOrderNo, order.getOrderNo())
                .eq(TradeOrder::getStatus, expected.name())
                .set(TradeOrder::getStatus, order.getStatus().name())
                // ⚠ 单号只在非空时写：本域没有「撤销发货」这个动作，shipNo 一经写入不再清空，
                //    故不需要 lambdaUpdate().set(col, null) 那条清空的写法
                .set(order.getShipNo() != null, TradeOrder::getShipNo, order.getShipNo())
                .update();
        if (!updated) {
            // 库里已经不在「来时状态」了。⚠ 提示语**不自己写**：拿库里的当前状态再跑一次状态机断言，
            // 于是「重复提交」（库里已等于目标态）与「被别的动作抢先」都得到与**顺序调用**同一句 400
            // （同一句提示只此一份，见 OrderStatusFlow#assertCanTransition）。
            // 订单不存在也走这一支：currentStatus 会在那儿抛 IllegalStateException。
            statusFlow.assertCanTransition(currentStatus(order.getOrderNo()), order.getStatus());
            // 上面那句按定义必抛（0 行 = 库里既不等于来时状态、也就不是「下标 +1」那一格）。
            // 保留兜底是为了不让「断言没抛」变成一次**静默的成功**——那正是本方法要防的东西。
            throw new ServiceException(400, "订单 " + order.getOrderNo() + " 的状态已在别处变更，请刷新后重试");
        }

        // 轨迹只补**缺失的尾巴**：按 seq 比对已有最大下标，重复调用即无新行可插（幂等）
        Integer maxSeq = statusLogService.list(Wrappers.<TradeOrderStatusLog>lambdaQuery()
                        .eq(TradeOrderStatusLog::getOrderNo, order.getOrderNo()))
                .stream()
                .map(TradeOrderStatusLog::getSeq)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1);
        insertStatusTrail(order.getOrderNo(), order.getStatusTrail(), maxSeq + 1, LocalDateTime.now(clock));
    }

    /**
     * 本次迁移的**来时状态** = 轨迹的倒数第二项（条件更新的前置条件就取它）
     *
     * <p>⚠ 为什么从**轨迹**取而不是新加一个字段/入参：轨迹是「这笔单走过哪些状态」的唯一事实源，
     * 末项是当前状态（由 {@code OrderStatusFlow#transition} 保证），故倒数第二项就是「这一步从哪来」。
     * 另立一个 API 会让同一件事有两个来源，且调用方多一个能传错的地方。</p>
     *
     * @param order 已迁移过状态的订单（其 {@code statusTrail} 是完整轨迹）
     * @return 来做状态
     * @throws IllegalStateException 轨迹不足两项（没有「来时状态」，说明调用方拿了个没迁移过的模型来 update）
     */
    private static OrderStatus previousStatus(OrderModel order) {
        List<OrderStatus> trail = order.getStatusTrail();
        if (trail.size() < 2) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 的状态轨迹只有 " + trail.size()
                    + " 项，没有「来时状态」，不能做状态变更落库");
        }
        return trail.get(trail.size() - 2);
    }

    /**
     * 读库里这笔单的**当前状态**（只在条件更新 0 行时用，用途是生成与顺序调用同一句 400）
     *
     * @param orderNo 业务可读单号
     * @return 库里的当前状态
     * @throws IllegalStateException 订单不在库里，或 status 列的值不是合法枚举名（数据被写坏）
     */
    private OrderStatus currentStatus(String orderNo) {
        TradeOrder row = orderService.getOne(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getOrderNo, orderNo));
        if (row == null) {
            throw new IllegalStateException("订单 " + orderNo + " 不在库里，无法更新状态");
        }
        return parseEnum(OrderStatus.class, row.getStatus(), orderNo, "status");
    }

    /**
     * 写一笔新订单：主表 + 明细 + 状态轨迹（初始态）
     */
    private void insertOrder(OrderModel order) {
        OrderAddress address = order.getAddress();
        TradeOrder row = new TradeOrder();
        row.setOrderNo(order.getOrderNo());
        row.setCustomerId(order.getCustomerId());
        row.setStoreId(order.getStoreId());
        row.setStoreName(order.getStoreName());
        row.setSource(order.getSource().name());
        row.setRequestId(order.getRequestId());
        row.setFingerprint(order.getFingerprint());
        row.setStatus(order.getStatus().name());
        row.setTotalQuantity(order.getTotalQuantity());
        row.setTotalAmount(order.getTotalAmount());
        row.setReceiverName(address.receiverName());
        row.setReceiverPhone(address.receiverPhone());
        row.setReceiverRegion(address.region());
        row.setReceiverDetail(address.detail());
        row.setShipNo(order.getShipNo());
        // ⚠ 显式写下单时刻：L2 幂等窗口以这一列为基准，交给自动填充会变成「写库那一刻」
        row.setCreateTime(order.getCreateTime());
        orderService.save(row);

        for (OrderItem item : order.getItems()) {
            TradeOrderItem itemRow = new TradeOrderItem();
            itemRow.setOrderNo(order.getOrderNo());
            itemRow.setSpuId(item.getSpuId());
            itemRow.setSkuId(item.getSkuId());
            itemRow.setGoodsName(item.getGoodsName());
            itemRow.setMainImage(item.getMainImage());
            itemRow.setSpecAttrs(writeSpecAttrs(item.getSpecAttrs()));
            itemRow.setUnitPrice(item.getUnitPrice());
            itemRow.setQuantity(item.getQuantity());
            itemRow.setSubtotal(item.getSubtotal());
            itemService.save(itemRow);
        }

        // 新单的轨迹就是初始状态（seq 0），发生时刻 = 下单时刻；
        // 后续变更走 update(...)，那时插入的行取「变更时刻」
        insertStatusTrail(order.getOrderNo(), order.getStatusTrail(), 0, order.getCreateTime());
    }

    /**
     * 从 {@code fromSeq} 起补写状态轨迹（已存在的 seq 不重插）
     *
     * @param orderNo 订单号
     * @param trail   模型上的完整轨迹（下标即 seq）
     * @param fromSeq 从哪个下标开始写（= 库里已有最大 seq + 1）
     * @param changedAt 这些行的**变更时刻**
     * @throws IllegalStateException 轨迹里某个状态在状态机配置里的下标与 seq 对不上
     *                               （轨迹与配置不是同一套口径，写下去就是一条假轨迹）
     */
    private void insertStatusTrail(String orderNo, List<OrderStatus> trail, int fromSeq, LocalDateTime changedAt) {
        List<OrderStatus> configured = statusFlow.statuses();
        for (int seq = fromSeq; seq < trail.size(); seq++) {
            OrderStatus status = trail.get(seq);
            if (configured.indexOf(status) != seq) {
                throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹第 " + seq + " 项是 " + status.name()
                        + "，它在状态机配置里的下标是 " + configured.indexOf(status)
                        + "（轨迹与配置不是同一套顺序），不能落库");
            }
            TradeOrderStatusLog log = new TradeOrderStatusLog();
            log.setOrderNo(orderNo);
            log.setSeq(seq);
            log.setStatus(status.name());
            // ⚠ 这一列就是「状态变更时刻」（DDL 注释），不是审计意义上的创建时间
            log.setCreateTime(changedAt);
            statusLogService.save(log);
        }
    }

    // ── 读侧 ────────────────────────────────────────────────────────────────────

    @Override
    public List<OrderModel> findBySubmissionId(long submissionId) {
        List<TradeOrderSubmissionOrder> links = submissionOrderService.list(
                Wrappers.<TradeOrderSubmissionOrder>lambdaQuery()
                        .eq(TradeOrderSubmissionOrder::getSubmissionId, submissionId)
                        .orderByAsc(TradeOrderSubmissionOrder::getId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<String> orderNos = links.stream().map(TradeOrderSubmissionOrder::getOrderNo).toList();
        Map<String, TradeOrder> byOrderNo = orderService
                .list(Wrappers.<TradeOrder>lambdaQuery().in(TradeOrder::getOrderNo, orderNos))
                .stream()
                .collect(Collectors.toMap(TradeOrder::getOrderNo, Function.identity()));
        // ⚠ 按关联表的顺序组装（= 首次返回的顺序），不能用查回来的顺序：库里的返回顺序没有承诺
        List<TradeOrder> rows = new ArrayList<>(orderNos.size());
        for (String orderNo : orderNos) {
            TradeOrder row = byOrderNo.get(orderNo);
            if (row == null) {
                throw new IllegalStateException("提交记录 " + submissionId + " 关联的订单 " + orderNo
                        + " 在 trade_order 里不存在（关联表与订单表不一致）");
            }
            rows.add(row);
        }
        return assemble(rows);
    }

    @Override
    public Optional<OrderModel> findRecentByFingerprint(String fingerprint, LocalDateTime since) {
        if (fingerprint == null || since == null) {
            return Optional.empty();
        }
        TradeOrder row = orderService.getOne(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getFingerprint, fingerprint)
                // 闭区间：窗口起点那一刻算「窗口内」（口径在 OrderRepository 的接口注释里）
                .ge(TradeOrder::getCreateTime, since)
                // 同指纹可能有多笔（窗口外的正常需求），取最早的一笔 = 第一次那次提交记下的那笔；
                // LIMIT 1 是为了让 getOne 不去抛「查到多行」
                .orderByAsc(TradeOrder::getCreateTime)
                .orderByAsc(TradeOrder::getId)
                .last("LIMIT 1"));
        return row == null ? Optional.empty() : Optional.of(assemble(List.of(row)).get(0));
    }

    @Override
    public boolean existsByOrderNo(String orderNo) {
        return orderNo != null && orderService.exists(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getOrderNo, orderNo));
    }

    @Override
    public Optional<OrderModel> findOrder(OrderQuery query) {
        // ⚠ 与分页走**同一份**条件构造（filterWrapper → applyCommonFilters）：详情没有「第二套」翻译口径。
        //    写成两处时，改好分页那处、漏了这里不会有任何东西报错——正是「漏传作用域 = 静默取任意一笔」那个洞。
        return findOne(filterWrapper(query));
    }

    @Override
    public OrderPage pageOrders(OrderPageQuery query) {
        return page(query);
    }

    // ── 内部：查询与组装 ────────────────────────────────────────────────────────

    private Optional<OrderModel> findOne(LambdaQueryWrapper<TradeOrder> wrapper) {
        TradeOrder row = orderService.getOne(wrapper);
        return row == null ? Optional.empty() : Optional.of(assemble(List.of(row)).get(0));
    }

    /**
     * 分页查询（条件一律经 {@link #filterWrapper} 构造，本方法只负责翻页与组装）
     */
    private OrderPage page(OrderPageQuery query) {
        Page<TradeOrder> page = orderService.page(new Page<>(query.pageNum(), query.pageSize()), filterWrapper(query));
        return new OrderPage(page.getTotal(), assemble(page.getRecords()));
    }

    /**
     * 构造一份「**作用域 + 筛选 + 排序**」查询条件——分页（{@link #pageOrders}）与详情（{@link #findOrder}）
     * 共用这**唯一**一个入口
     *
     * <p>⚠ 详情必须也走这里：它是一个写路径的前置读（改状态前先取单），而「作用域没进查询」在详情上的后果
     * 比在分页上更重——分页会多返回几行，详情会**取到别人的那一笔并改它**。两者共用一份翻译，
     * 「改了一处漏了另一处」这个改法才不成立。单测 {@code JdbcOrderRepositoryFiltersTest} 钉的就是它。</p>
     */
    static LambdaQueryWrapper<TradeOrder> filterWrapper(OrderPageQuery query) {
        LambdaQueryWrapper<TradeOrder> wrapper = Wrappers.lambdaQuery();
        applyCommonFilters(wrapper, query);
        return wrapper;
    }

    /**
     * 追加分页共用的**作用域 + 筛选 + 排序**（四项都是**可选**的：null = 不筛 / 不限定）
     *
     * <p>⚠ <b>状态名必须先取出来再进 {@code eq}</b>：{@code eq(condition, column, value)} 的 {@code value}
     * 是**无条件求值**的实参，写成 {@code eq(query.status() != null, …, query.status().name())} 时，
     * 「不筛状态」这条**默认路径**（{@code status == null}）会在进 {@code eq} 之前就 NPE——
     * 条件为 {@code false} 也拦不住它，因为实参先算。⚠ 同一个坑：任何「先解引用、再当条件值传」的写法
     * 都等价于把该条件删掉，只在 null 时变成 500。<br>
     * 两个作用域字段传的是 {@code query.customerId()} / {@code query.storeId()} 这类**裸取值**，
     * 没有解引用，故不需要额外的局部变量。</p>
     *
     * <p>⚠ <b>作用域与筛选条件的翻译只此一处</b>：分页（{@link #pageOrders}）与详情（{@link #findOrder}）
     * 都经 {@link #filterWrapper} 调用本方法，没有任何第二条翻译路径。单测
     * {@code JdbcOrderRepositoryFiltersTest} 直接钉这一段——它守的是「不传即不限定」这条默认路径：
     * 省略任一作用域时，SQL 里都不能多出对应条件。</p>
     */
    static void applyCommonFilters(LambdaQueryWrapper<TradeOrder> wrapper, OrderPageQuery query) {
        String statusName = query.status() == null ? null : query.status().name();
        wrapper.eq(query.orderNo() != null, TradeOrder::getOrderNo, query.orderNo())
                .eq(statusName != null, TradeOrder::getStatus, statusName)
                .eq(query.customerId() != null, TradeOrder::getCustomerId, query.customerId())
                .eq(query.storeId() != null, TradeOrder::getStoreId, query.storeId())
                .orderByDesc(TradeOrder::getId);
    }

    /**
     * 把订单行组装成聚合（**保留传入顺序**）
     *
     * <p>⚠ 明细与轨迹各用**一条** {@code IN} 查回来再按订单号分组：逐单查是 N+1，
     * 一页 100 笔就是 200 次往返，而这里的代价只有固定的 2 次。</p>
     */
    private List<OrderModel> assemble(List<TradeOrder> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> orderNos = rows.stream().map(TradeOrder::getOrderNo).toList();
        Map<String, List<TradeOrderItem>> itemsByOrderNo = itemService
                .list(Wrappers.<TradeOrderItem>lambdaQuery()
                        .in(TradeOrderItem::getOrderNo, orderNos)
                        .orderByAsc(TradeOrderItem::getOrderNo)
                        .orderByAsc(TradeOrderItem::getSkuId))
                .stream()
                .collect(Collectors.groupingBy(TradeOrderItem::getOrderNo, LinkedHashMap::new, Collectors.toList()));
        Map<String, List<TradeOrderStatusLog>> trailByOrderNo = statusLogService
                .list(Wrappers.<TradeOrderStatusLog>lambdaQuery()
                        .in(TradeOrderStatusLog::getOrderNo, orderNos)
                        .orderByAsc(TradeOrderStatusLog::getOrderNo)
                        .orderByAsc(TradeOrderStatusLog::getSeq))
                .stream()
                .collect(Collectors.groupingBy(TradeOrderStatusLog::getOrderNo, LinkedHashMap::new, Collectors.toList()));

        List<OrderModel> orders = new ArrayList<>(rows.size());
        for (TradeOrder row : rows) {
            orders.add(toOrderModel(row,
                    itemsByOrderNo.getOrDefault(row.getOrderNo(), List.of()),
                    trailByOrderNo.getOrDefault(row.getOrderNo(), List.of())));
        }
        return List.copyOf(orders);
    }

    /**
     * 单笔订单：行 + 轨迹 → 聚合，并做四项对账
     */
    private OrderModel toOrderModel(TradeOrder row, List<TradeOrderItem> itemRows, List<TradeOrderStatusLog> trailRows) {
        String orderNo = row.getOrderNo();

        List<OrderItem> items = new ArrayList<>(itemRows.size());
        for (TradeOrderItem itemRow : itemRows) {
            items.add(OrderItem.rehydrate(itemRow.getSkuId(), itemRow.getQuantity(), itemRow.getSpuId(),
                    itemRow.getGoodsName(), itemRow.getMainImage(),
                    readSpecAttrs(itemRow.getSpecAttrs(), orderNo, itemRow.getSkuId()),
                    itemRow.getUnitPrice(), itemRow.getSubtotal()));
        }

        List<OrderStatus> trail = new ArrayList<>(trailRows.size());
        for (int seq = 0; seq < trailRows.size(); seq++) {
            TradeOrderStatusLog log = trailRows.get(seq);
            if (log.getSeq() == null || log.getSeq() != seq) {
                throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹 seq 不连续（第 " + (seq + 1)
                        + " 行的 seq=" + log.getSeq() + "），轨迹被改坏");
            }
            trail.add(parseEnum(OrderStatus.class, log.getStatus(), orderNo, "trade_order_status_log.status"));
        }

        OrderAddress address;
        try {
            address = new OrderAddress(row.getReceiverName(), row.getReceiverPhone(),
                    row.getReceiverRegion(), row.getReceiverDetail());
        } catch (ServiceException e) {
            // ⚠ 地址值对象对**用户输入**抛 400（可直接展示给顾客）；这里是「库里读出来的地址不合法」，
            //    是数据被写坏，不能报成 400 让页面以为是自己填错了
            throw new IllegalStateException("订单 " + orderNo + " 落库的收货地址不合法（" + e.getMessage() + "），不能重建", e);
        }

        OrderModel order = OrderModel.rehydrate(orderNo, row.getCustomerId(), row.getStoreId(), row.getStoreName(),
                parseEnum(OrderSource.class, row.getSource(), orderNo, "trade_order.source"),
                address, row.getRequestId(), row.getFingerprint(), row.getShipNo(), row.getCreateTime(),
                items, trail, statusFlow);

        reconfirmTotals(order, row);
        return order;
    }

    /**
     * 落库的总件数 / 总金额与按订单行重算的值对账
     *
     * <p>⚠ 这两列是**派生量的落库副本**：派生量永远是「按行求和」，两列一旦对不上，说明写的时候
     * 或读的时候有一处错了。不查的话，页面上的总额会静默不等于明细之和——顾客最容易发现、
     * 也最没法解释的一类错误。</p>
     */
    private static void reconfirmTotals(OrderModel order, TradeOrder row) {
        if (row.getTotalQuantity() == null || row.getTotalQuantity() != order.getTotalQuantity()) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 落库的总件数(" + row.getTotalQuantity()
                    + ")与按明细重算的(" + order.getTotalQuantity() + ")不一致");
        }
        if (row.getTotalAmount() == null || row.getTotalAmount().compareTo(order.getTotalAmount()) != 0) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 落库的总金额(" + row.getTotalAmount()
                    + ")与按明细重算的(" + order.getTotalAmount().toPlainString() + ")不一致");
        }
    }

    /**
     * 枚举名 → 常量（不认识的名字说明数据被写坏，不是用户输入问题）
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String orderNo, String column) {
        if (raw == null) {
            throw new IllegalStateException("订单 " + orderNo + " 的 " + column + " 为空，不能重建");
        }
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("订单 " + orderNo + " 的 " + column + "=" + raw
                    + " 不是合法的 " + type.getSimpleName() + " 常量", e);
        }
    }

    /**
     * 规格 Map → JSON 文本（{@code spec_attrs} 列 NOT NULL，故无规格也要写 {@code {}}）
     */
    private String writeSpecAttrs(Map<String, String> specAttrs) {
        try {
            return objectMapper.writeValueAsString(specAttrs == null ? Map.of() : specAttrs);
        } catch (JsonProcessingException e) {
            // 规格来自商品快照（纯字符串键值），序列化失败只可能是编程错误
            throw new IllegalStateException("订单规格序列化失败：" + e.getMessage(), e);
        }
    }

    /**
     * JSON 文本 → 规格 Map（解析失败说明落库的 JSON 被写坏）
     *
     * @return 规格键值对；{@code null} / 空白 / 空对象都归一成空 Map（与 {@code OrderItem#rehydrate} 的约定一致）
     */
    private Map<String, String> readSpecAttrs(String json, String orderNo, Long skuId) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("订单 " + orderNo + " 明细 skuId=" + skuId + " 的规格 JSON 不是键值对："
                    + e.getMessage(), e);
        }
    }
}
