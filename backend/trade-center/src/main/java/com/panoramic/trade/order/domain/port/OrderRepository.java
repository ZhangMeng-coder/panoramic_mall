package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderModel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单仓库端口：**先占键**的两级幂等 + 一次提交的整批落库 + 三侧读。
 *
 * <h3>为什么是「先占键」而不是「查 → 做 → 写」</h3>
 * <p>上一版端口是 check-then-act（先查提交记录 → 一路下单扣库存 → 最后写记录）。它在并发下有
 * <b>键没被独占</b>的缺口：两个线程同时提交同一个 {@code customerId + requestId}（双击、超时重试
 * ——正是幂等要挡的那类）会**都**错过查询、各自生成单号扣库存，而落库时只能留下一批，
 * 另一批成了「一级幂等永远取不到的孤儿单」——那不是回滚能解决的问题（{@code @GlobalTransactional} 也挡不住）。</p>
 *
 * <p>本版把「占用幂等键」提到**执行业务之前**：{@link #occupy} 往
 * {@code trade_order_submission} 插一行，其 {@code (customer_id, request_id)} 唯一键即幂等键。
 * 于是并发由**数据库的唯一索引**串行化，而不是由应用层的查询时机决定：</p>
 * <ul>
 *   <li>后到者**阻塞**在唯一索引的行锁上，直到先到者提交或回滚；</li>
 *   <li>先到者提交 → 后到者拿到重复键 → 回查键 → {@link #findBySubmissionId} 回读**先到者那一批**原样返回；</li>
 *   <li>先到者业务失败回滚 → 键随事务**一起消失**，后到者插入成功、正常下单。
 *       故「失败不留残键」不需要任何补偿动作：键与订单在**同一个事务**里，原子性由事务给。</li>
 * </ul>
 * <p>⚠ <b>前提是「先占键」与「写订单」在同一事务内</b>：{@link #occupy} 若自己单独提交，
 * 键就会先落地——先到者随后失败时键残留，重放会回读到一个**空批次**（顾客看到「0 笔订单」），
 * 而后到者也不再阻塞。事务边界因此必须**在编排层**（{@code OrderCreateCoordinator#create}），
 * 不在本端口的任一方法上。</p>
 * <p>⚠ 已知代价：先到者若长时间不提交，后到者会阻塞到 {@code innodb_lock_wait_timeout}（默认 50s）。
 * 这是「宁可慢也不重复下单」的取舍。</p>
 *
 * <p>⚠ <b>落库实现还有一个隐式前提：回读赢家之前，本事务不能有任何一致性读</b>
 * ——{@code REPEATABLE READ} 的读视图一旦建立就固定，赢家若在读视图之后才提交，它的行就看不见。
 * 当前成立：本方法在撞唯一键之前只发过那一条 INSERT，读视图正是由回读的查询首次建立的，
 * 而它严格晚于赢家的提交（赢家未提交则后到者阻塞在唯一索引上；赢家回滚则 INSERT 直接成功、
 * 根本走不到回读）。故 ⚠ <b>不要在编排层 {@code occupy} 之前加任何普通 SELECT</b>：
 * 加了就可能让重放读到**空批**（{@link #findBySubmissionId} 会返回空表），顾客看到「0 笔订单」
 * 而库里其实有单——一次静默的错误结果，不报错、只骗人。</p>
 *
 * <h3>为什么「本次提交包含哪几笔」要单独记一份关联</h3>
 * <p>一次提交可能拆成多笔（一单一店，裁定 D3），其中「指纹命中复用」的那几笔属于**上一次提交**——
 * 它们的 {@code requestId} 记的是「哪次提交创造了这笔单」，不能改写成本次的（改了就把上一次的凭证抹掉了）。
 * 于是「本次提交包含哪些笔」在订单行上根本不成立：按 requestId 查只会查到本次**新建**的笔，
 * 重放时少返回复用的笔——同一个请求两次调用返回的条数都不一样，客户端只会以为丢了单。
 * 故落库时另记 {@code trade_order_submission_order}（提交记录 id ↔ 订单号），
 * 它才是 {@link #findBySubmissionId} 的依据。⚠ 它**不是缓存**：窗口外的重放只剩它挡得住。</p>
 *
 * <h3>两级幂等各自的作用域</h3>
 * <p>{@code requestId} 的作用域是**顾客内**（它由客户端生成，不同顾客之间不共享），故
 * {@link #occupy} 的键是 {@code (customerId, requestId)} 两列——只按键查会让 A 顾客的订单被
 * B 顾客用同一个键捞走；第二级的指纹里本来就含顾客 id，天然不跨顾客。</p>
 *
 * <p>⚠ 读侧同样按锚点分方法（顾客 / 商户 / 平台），无锚点的那一个只服务平台全量视角——
 * 见 {@link OrderQuery} 与 {@link PlatformOrderQuery} 的说明。</p>
 */
public interface OrderRepository {

    /**
     * 占用幂等键（第一级幂等）：插入 {@code (customerId, requestId)} 的提交记录
     *
     * <p>⚠ 本方法必须在**事务内**被调用，且该事务要覆盖到订单落库（见接口注释）。</p>
     *
     * <p>⚠ {@code requestId} 为 null / 空白时表示「这次提交不做请求级去重」：实现侧照常插一行
     * （{@code request_id} 为 NULL，MySQL 唯一索引允许多行 NULL，故永不冲突），
     * 于是 {@code submissionId} 始终有值、关联表始终可写，而重放永远走不到这条键上。</p>
     *
     * @param customerId 下单顾客 id（幂等键的收窄维度，不可省）
     * @param requestId  调用方带来的幂等键（可为 null）
     * @return 占键结果（{@code created=false} 表示键已被占用，调用方应回读整批）
     */
    OccupyResult occupy(Long customerId, String requestId);

    /**
     * 按提交记录 id 回读那次提交返回的**整批**订单（先占键撞车后的唯一出路）
     *
     * <p>顺序 = 关联表的插入顺序 = 首次返回的顺序（拆单后按 storeId 升序），
     * 故重放拿到的条数与排列与首次逐一致。</p>
     *
     * @param submissionId 提交记录 id（来自 {@link #occupy}）
     * @return 那批订单；提交记录存在但没有任何关联时为**空列表**（不该发生：键与关联同事务写入）
     */
    List<OrderModel> findBySubmissionId(long submissionId);

    /**
     * 按指纹在时间窗口内查一笔订单（第二级幂等）
     *
     * <p>窗口是**闭区间**（{@code createTime >= since}）：边界那一刻算「窗口内」。
     * 窗口外同指纹是**新单**——「同一顾客过一会儿又买同一批东西」是真实需求，不是重复提交。</p>
     *
     * <p>⚠ 返回「最早的一笔」而不是「那一笔」：同指纹的多笔在**窗口外**是正常的（上面那句），
     * 同一次查询窗口内理论上至多一笔，但配置被调小、并发双击这类情况下可能出现两笔，
     * 那时取最早的一笔＝取「第一次那次提交记下的那笔」，语义唯一且确定（{@code create_time, id} 升序）。</p>
     *
     * @param fingerprint 订单指纹（由 {@code OrderFingerprint} 算出，不含金额与时间）
     * @param since       窗口起点：只认 {@code createTime >= since} 的订单
     * @return 命中的那一笔；没有则空
     */
    Optional<OrderModel> findRecentByFingerprint(String fingerprint, LocalDateTime since);

    /**
     * 单号是否已被占用（「生成 → 查重 → 重试」用）
     *
     * @param orderNo 业务可读单号
     * @return 已存在则为 {@code true}
     */
    boolean existsByOrderNo(String orderNo);

    /**
     * 落库一次提交新建的订单（订单 + 明细 + 初始状态轨迹 + 提交关联），**一次写入**
     *
     * <p>⚠ 与 {@link #occupy} 同事务：订单落了而关联没落，重放会退化成第二级指纹复用
     * （窗口内各笔都还能命中 → 仍返回同一批），但**窗口外**的重放没有任何东西挡得住，会真的再下一单。</p>
     *
     * <p>⚠ 按订单号**幂等**：一批里可能含复用笔（此前提交留下、本次指纹命中的订单），
     * 它们已在库里，不得重复插入（明细与轨迹同理）；但关联行**无论如何都要写**
     * ——本次提交的成员关系是新的。</p>
     *
     * @param submissionId 本次提交的记录 id（来自 {@link #occupy}）
     * @param orders       本次提交返回的**整批**订单（含复用笔；已封存、状态为待支付）
     */
    void saveAll(long submissionId, List<OrderModel> orders);

    /**
     * 状态变更后的落库（支付 / 发货 / 收货三个动作调用）
     *
     * <p>只写「状态 + 快递单号 + 状态轨迹的**缺失尾巴**」：订单行与金额在封存后不再变（裁定见
     * {@code OrderModel#seal}）；轨迹按 {@code seq} 比对，已有的行不会重复插入。</p>
     *
     * <p>⚠ <b>它是一次**条件更新**，不是按主键盲写</b>：库里必须仍处在「来时状态」
     * （= 轨迹的倒数第二项，见 {@code OrderModel#getStatusTrail()}），否则本次写入是一次**丢失更新**
     * ——两个人同时点「发货」时，盲写会让后到者把状态列推回旧值，而轨迹的唯一键又插不进去，
     * 于是状态列与轨迹互相矛盾（列表页按状态列、详情页按轨迹重建，同一笔单显示两个状态）。</p>
     *
     * <p>⚠ <b>因此本方法不再是「重放无副作用」</b>：库里已不在来时状态时抛
     * {@code ServiceException}(400)，且提示语与**顺序调用**同一个动作得到的完全一致
     * （由状态机生成，见 {@code OrderStatusFlow#assertCanTransition}）——「重复动作该不该 400」
     * 只有一个答案，不该因为「两次请求是否撞在一起」而给出两种结果（R8：重复动作由状态机拒，
     * 而不是静默成功、也不是 500）。</p>
     *
     * @param order 已迁移过状态的订单（其 {@code statusTrail} 是完整轨迹，末项 = 目标状态）
     * @throws com.panoramic.common.exception.ServiceException 库里已不在来时状态（HTTP 400）
     */
    void update(OrderModel order);

    /**
     * 按订单号查（**平台侧**：管理端是全量视角，没有锚点）
     *
     * @param orderNo 业务可读单号
     * @return 订单；不存在则空
     */
    Optional<OrderModel> findByOrderNo(String orderNo);

    /**
     * 按顾客 + 订单号查（**顾客侧**：数据权限锚点在查询里，不是查回来再判）
     *
     * <p>⚠ 锚点参与收窄是刻意的：写成「查回来再比 customerId」时，漏了那句判断就是越权读别人的订单，
     * 而漏判不会报错、只会静默返回。锚点进查询之后，这类调用在 SQL 层就取不到数据。</p>
     *
     * @param customerId 顾客 id（锚点）
     * @param orderNo    业务可读单号
     * @return 属于该顾客的订单；不属于或不存在都为空
     */
    Optional<OrderModel> findByCustomerOrderNo(Long customerId, String orderNo);

    /**
     * 按店铺 + 订单号查（**商户侧**：锚点在查询里）
     *
     * @param storeId 店铺 id（锚点）
     * @param orderNo 业务可读单号
     * @return 属于该店铺的订单；不属于或不存在都为空
     */
    Optional<OrderModel> findByStoreOrderNo(Long storeId, String orderNo);

    /**
     * 顾客侧分页（只含该顾客的订单）
     *
     * @param customerId 顾客 id（锚点）
     * @param query      筛选与分页条件（不含锚点）
     * @return 总数 + 当页订单
     */
    OrderPage pageCustomerOrders(Long customerId, OrderQuery query);

    /**
     * 商户侧分页（只含该店铺的订单）
     *
     * <p>⚠ 含 {@code PENDING_PAYMENT}（裁定 R4）：商户侧的待办起点不是「已支付」——
     * 店主需要看见下单后还没付款的那些单，否则无法判断自己要不要备货。</p>
     *
     * @param storeId 店铺 id（锚点）
     * @param query   筛选与分页条件（不含锚点）
     * @return 总数 + 当页订单
     */
    OrderPage pageStoreOrders(Long storeId, OrderQuery query);

    /**
     * 平台侧分页（全量视角，无锚点）
     *
     * <p>⚠ 它与上面两个方法的入参**不是同一个类型**：平台侧多一个「按店铺 / 顾客筛」的能力
     * （见 {@link PlatformOrderQuery}），而那两个能力对顾客 / 商户侧本就不该存在。</p>
     *
     * @param query 筛选与分页条件（不含锚点；含平台侧特有的可选 {@code storeId} / {@code customerId} 筛选）
     * @return 总数 + 当页订单
     */
    OrderPage pagePlatformOrders(PlatformOrderQuery query);
}
