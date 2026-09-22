package com.panoramic.trade.order.infrastructure.inmemory;

import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.port.OccupyResult;
import com.panoramic.trade.order.domain.port.OrderPage;
import com.panoramic.trade.order.domain.port.OrderPageQuery;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.domain.port.OrderRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@link OrderRepository} 的内存实现（先占键形状的参照实现）。
 *
 * <p>⚠ <b>临时脚手架</b>：真实落库已是 {@code JdbcOrderRepository}（默认），本类保留下来是给
 * 单测与「无数据源」场景用的（{@code panoramic.trade.order.repository=memory}）。
 * 商品 / 库存两个下游的内存脚手架**已随 T4b 移入测试树**（{@code src/test/.../support/InMemoryGoodsQueryPort} /
 * {@code InMemoryStockPort}）——它们只服务单测，不再是可装配的生产分支；本类则因上面那个开关仍在 main。</p>
 *
 * <p>⚠ <b>它必须能表达「未提交的占位」</b>，否则先占键的语义在单测里就不成立：真实实现里
 * 键与订单在同一个事务里——业务失败则**键随事务一起消失**。内存实现没有事务，于是用一个
 * 两段式的写法把同一件事演出来：{@link #occupy} 只登记一个**占位**（不写进「已提交的键」），
 * 直到 {@link #saveAll} 才把它提升为已提交。于是：</p>
 * <ul>
 *   <li>占位后业务失败（{@code saveAll} 从未被调用）→ 该键不在已提交集合里，下次同键的
 *       {@code occupy} 正常拿到 {@code created=true}（= 键被释放）；</li>
 *   <li>占位后 {@code saveAll} → 键进入已提交集合，之后再 {@code occupy} 同键得到
 *       {@code created=false} + 先到者的 {@code submissionId}（= 幂等命中）。</li>
 * </ul>
 * <p>⚠ <b>并发不在本类的保证范围内</b>：真实实现的并发由唯一索引的行锁串行化（后到者阻塞到先到者
 * 提交，再拿到重复键），内存实现只保证单线程下的语义一致，不做阻塞模拟——见
 * {@link OrderRepository#occupy} 的契约，那才是唯一的并发口径来源。</p>
 *
 * <p>⚠ 批次里存的是**活引用不是快照**：重放返回的是那批订单的当前状态（订单生命周期自己会推进，
 * 重放看到最新真相，不会拿到过期状态）。代价是「这份记录是唯一凭证」只成立于**没人改它**的前提下——
 * 返回值与仓库里是同一批对象，谁改了返回值就等于改了凭证。</p>
 */
public class InMemoryOrderRepository implements OrderRepository {

    /** 已写入的订单（写入顺序 = 拆单顺序；读接口各自过滤与排序） */
    private final List<OrderModel> orders = new ArrayList<>();

    /** 占位/提交记录：submissionId → 幂等键（{@code null} = 这次提交没带 requestId，不做请求级去重） */
    private final Map<Long, String> keyBySubmission = new LinkedHashMap<>();

    /** **已提交**的幂等键 → submissionId（第一级幂等的唯一凭证） */
    private final Map<String, Long> committedKeys = new LinkedHashMap<>();

    /** **已提交**的批次：submissionId → 那次提交返回的整批（含复用笔，顺序即首次返回顺序） */
    private final Map<Long, List<OrderModel>> committedBatches = new LinkedHashMap<>();

    private long nextSubmissionId = 1;

    // ── 写侧 ────────────────────────────────────────────────────────────────────

    @Override
    public synchronized OccupyResult occupy(Long customerId, String requestId) {
        String key = submissionKey(customerId, requestId);
        if (key != null) {
            Long committed = committedKeys.get(key);
            if (committed != null) {
                return new OccupyResult(committed, false);
            }
        }
        // 无论是「首次占位」还是「上一次的占位没提交（业务失败）后重来」，都发一个新 id：
        // 未提交的占位不被承认，故它不会被回读到，也就不需要显式释放
        long submissionId = nextSubmissionId++;
        keyBySubmission.put(submissionId, key);
        return new OccupyResult(submissionId, true);
    }

    @Override
    public synchronized void saveAll(long submissionId, List<OrderModel> batch) {
        for (OrderModel order : batch) {
            // 按单号幂等：一批里可能含复用笔（上一次提交留下的订单），它们已经在列表里了
            if (!existsByOrderNo(order.getOrderNo())) {
                orders.add(order);
            }
        }
        committedBatches.put(submissionId, List.copyOf(batch));
        String key = keyBySubmission.get(submissionId);
        if (key != null) {
            // putIfAbsent：同一键上「首次那批」才是唯一凭证，后到的提交不得覆盖它
            committedKeys.putIfAbsent(key, submissionId);
        }
    }

    @Override
    public synchronized void update(OrderModel order) {
        Objects.requireNonNull(order, "订单不能为空");
        // 内存实现里仓库持有的是**同一个对象**，状态迁移在聚合上发生时这里就已经是新的了；
        // 方法仍然要有，且要断言这笔单确实在仓库里——否则「更新了一笔不存在的单」会静默通过
        if (!existsByOrderNo(order.getOrderNo())) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 不在仓库里，无法更新");
        }
        // ⚠ 真实实现（JdbcOrderRepository）在这里还做一次**条件更新**：库里必须仍是「来时状态」，
        //    否则抛 400（重复动作 / 被别的动作抢先）。本类**表达不了**它——仓库持的是活引用，
        //    状态在聚合上已经改完，「来时状态」根本读不到了。故「丢失更新 / 并发重复动作 → 400」
        //    这一条在内存实现下验不到，只能在真库上验（T15 用顺序重复动作覆盖同一句提示）；
        //    把本类当成「先占键与读侧作用域」的参照实现，别当成并发语义的参照实现。
    }

    @Override
    public synchronized void updateAddress(OrderModel order) {
        Objects.requireNonNull(order, "订单不能为空");
        if (!existsByOrderNo(order.getOrderNo())) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 不在仓库里，无法更新");
        }
        // ⚠ 与 update(...) 同一处表达不了的东西：真实实现是「库里仍为待支付」的条件更新，
        //    0 行即 400。本类持的是活引用、地址在聚合上已经换完，「读之后被支付抢先」这一幕
        //    在内存实现下验不到——只能靠真库（同 update 的说明）。
    }

    // ── 读侧 ────────────────────────────────────────────────────────────────────

    @Override
    public synchronized List<OrderModel> findBySubmissionId(long submissionId) {
        return committedBatches.getOrDefault(submissionId, List.of());
    }

    @Override
    public synchronized Optional<OrderModel> findRecentByFingerprint(String fingerprint, LocalDateTime since) {
        if (fingerprint == null || since == null) {
            return Optional.empty();
        }
        return orders.stream()
                .filter(order -> fingerprint.equals(order.getFingerprint()))
                // 闭区间：窗口起点那一刻算「窗口内」（口径定义在 OrderRepository 的接口注释里）
                .filter(order -> !order.getCreateTime().isBefore(since))
                .findFirst();
    }

    @Override
    public synchronized boolean existsByOrderNo(String orderNo) {
        if (orderNo == null) {
            return false;
        }
        return orders.stream().anyMatch(order -> orderNo.equals(order.getOrderNo()));
    }

    @Override
    public synchronized Optional<OrderModel> findOrder(OrderQuery query) {
        // ⚠ 单号必填这道不变量与落库实现同处：详情只有两条实现，findOrder 是任何调用方都必经的地方
        //    （OrderQuery 是 public，绕过 forDetail 才拿得到「没有单号条件」的查询）
        query.requireOrderNo();
        // 两个作用域都是可选的：null = 不限定（与落库实现的「条件不入 SQL」同口径）
        // ⚠ status 也参与筛选，**以落库实现为准**（那边 status != null 即进 WHERE，见
        //    JdbcOrderRepository#applyCommonFilters）：本类只是替身，替身少一个条件就会有
        //    「只在内存实现上绿」的用例，把「详情也能按状态取」这条口径钉歪。
        return find(order -> orderNoMatches(query.orderNo(), order)
                && statusMatches(query.status(), order)
                && scopeMatches(query.customerId(), order.getCustomerId())
                && scopeMatches(query.storeId(), order.getStoreId()));
    }

    @Override
    public synchronized OrderPage pageOrders(OrderPageQuery query) {
        return page(query);
    }

    // ── 内部 ────────────────────────────────────────────────────────────────────

    private Optional<OrderModel> find(Predicate<OrderModel> predicate) {
        return orders.stream().filter(predicate).findFirst();
    }

    /**
     * 作用域过滤 + 条件筛选 + **下单倒序**分页
     *
     * <p>倒序与真实实现的 {@code ORDER BY id DESC} 同口径：最新的订单排在最前面，
     * 页面的「我的订单」第一眼看到的就是刚下的那笔。</p>
     *
     * <p>⚠ 入参是 {@link OrderPageQuery} 而不是某个具体的 record：**作用域与筛选条件的翻译只此一处**
     * （顾客 / 商户 / 管理端调用方全走同一条 {@link #pageOrders}），四项可选条件（订单号 / 状态 /
     * 两个作用域）的「不筛」语义与落库实现的条件拼接同口径。</p>
     */
    private OrderPage page(OrderPageQuery query) {
        List<OrderModel> matched = new ArrayList<>(orders.size());
        for (int i = orders.size() - 1; i >= 0; i--) {   // 倒序收集，省一次 reverse
            OrderModel order = orders.get(i);
            if (!scopeMatches(query.customerId(), order.getCustomerId())
                    || !scopeMatches(query.storeId(), order.getStoreId())) {
                continue;
            }
            if (query.orderNo() != null && !query.orderNo().equals(order.getOrderNo())) {
                continue;
            }
            if (query.status() != null && query.status() != order.getStatus()) {
                continue;
            }
            matched.add(order);
        }
        int from = (query.pageNum() - 1) * query.pageSize();
        if (from >= matched.size()) {
            return new OrderPage(matched.size(), List.of());
        }
        int to = Math.min(from + query.pageSize(), matched.size());
        return new OrderPage(matched.size(), matched.subList(from, to));
    }

    /**
     * 幂等键：顾客 id 与 requestId 一起拼
     *
     * <p>⚠ 只用 requestId 会让「A 顾客的订单被 B 顾客用同一个键捞走」；{@code customerId} 为 null 时
     * 退化成 {@code null|xxx}——那是「没带顾客身份」的自成一格，仍不会跨顾客串到别人的订单上。</p>
     *
     * @return 幂等键；{@code requestId} 为空时返回 {@code null}（表示这次提交不做请求级去重）
     */
    private static String submissionKey(Long customerId, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return customerId + "|" + requestId;
    }

    /**
     * 作用域命中判定：{@code scope == null} 表示**不限定**（管理端全量视角），不是「筛 null 值」
     *
     * <p>⚠ 与落库实现里 {@code eq(cond, col, value)} 的「不传即不入 SQL」是同一口径——
     * 两个实现必须给出同样的结果，否则同一份调用在两种装配下看到的数据不同。</p>
     */
    private static boolean scopeMatches(Long scope, Long value) {
        return scope == null || Objects.equals(scope, value);
    }

    /**
     * 订单号命中判定（{@code null} = 不筛；空白串已由 {@link OrderQuery#normalizeOrderNo} 归 null）
     */
    private static boolean orderNoMatches(String orderNo, OrderModel order) {
        return orderNo == null || orderNo.equals(order.getOrderNo());
    }

    /**
     * 状态命中判定：{@code status == null} 表示**不筛状态**（不是「筛 null 状态」）
     *
     * <p>⚠ 与落库实现 {@code eq(condition, col, value)} 的「不传即不入 SQL」同一口径——
     * 详情与分页都走这一条判定，替身的条件集合必须与落库实现逐项对齐。</p>
     */
    private static boolean statusMatches(OrderStatus status, OrderModel order) {
        return status == null || status == order.getStatus();
    }

    // ── 测试入口 ────────────────────────────────────────────────────────────────

    /**
     * 清空仓库（测试入口）
     *
     * <p>每个用例从一个空仓库开始，跨用例残留会让「仓库条数不变」这类断言失去意义。
     * 占位、已提交批次、订单一并清掉：只清一半会让下一个用例意外命中上一个用例的提交记录。</p>
     */
    public synchronized void clear() {
        orders.clear();
        keyBySubmission.clear();
        committedKeys.clear();
        committedBatches.clear();
        nextSubmissionId = 1;
    }

    /**
     * @return 已写入的订单笔数（测试入口：断言「失败不留残单」「复用不重复落库」）
     */
    public synchronized int count() {
        return orders.size();
    }

    /**
     * @return 全部订单（不可变副本，写入顺序；测试入口：需要整体核对时用）
     */
    public synchronized List<OrderModel> all() {
        return List.copyOf(orders);
    }

    /**
     * 按「顾客 + 请求 id」查**已提交**的那一批（测试入口）
     *
     * <p>⚠ 生产路径上没有这个方法：幂等命中是通过 {@link #occupy} 的返回值 + {@link #findBySubmissionId}
     * 表达的（两个来回变一个来回）。测试用它直接断言「首次那批是唯一凭证」这类不变量。</p>
     *
     * @return 已提交的那批订单；没提交过（含只占位未提交）则空
     */
    public synchronized Optional<List<OrderModel>> findCommittedBatch(Long customerId, String requestId) {
        String key = submissionKey(customerId, requestId);
        if (key == null) {
            return Optional.empty();
        }
        Long submissionId = committedKeys.get(key);
        return submissionId == null ? Optional.empty() : Optional.of(findBySubmissionId(submissionId));
    }
}
