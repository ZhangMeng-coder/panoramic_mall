package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.port.SkuSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 订单聚合根（todo 明确要求的名字）：一笔订单一店（裁定 D3），封装商品行、数量、金额、店铺与状态。
 *
 * <h3>为什么是「两段式生命周期」</h3>
 * <p>步骤链是配置驱动的（裁定 D9），步骤之间**只经本模型传参**（todo 原话）。于是模型必须先以
 * 「只有 skuId + 数量」的形态建起来，再由各步骤通过聚合方法补全：{@link #applyGoodsSnapshot} 填商品快照、
 * {@link #applyPrice} 填单价与小计。等到 {@link #seal} 时，模型自己校验「每一行都齐备、总额与行求和一致」——
 * 这样**步骤少跑一个、跑错顺序、算出不一致的金额，都会在建单的最后一步被拦住**，而不是等落库后才被发现。
 * 代价是模型对外有短暂的不完整状态，这是刻意的：<b>不完整的状态不允许被当成订单用</b>
 * （未 seal 的模型连状态都不能迁移，见 {@link #transitionTo}）。</p>
 *
 * <h3>为什么状态迁移要经 {@link OrderStatusFlow}</h3>
 * <p>「只前不退、不跨级」的顺序写在配置里而不是代码里（裁定 D7），本类只负责在迁移成功后**追加轨迹**
 * （{@link #getStatusTrail}，裁定 D16）。轨迹让「只前不退」在数据上可断言，而不是只靠「抛没抛异常」——
 * 一个被回滚过的状态变更不会留下痕迹，而轨迹会。</p>
 *
 * <h3>边界（含落库期的订正）</h3>
 * <p>本类不认识仓库、不认识库存、不认识时钟——订单号查重、两级幂等、拆单、库存扣减与失败回滚
 * 都在编排层（{@code OrderCreateCoordinator}）。<br>
 * ⚠ 分布式事务（Seata）**不在此处**：本类是无事务语义的纯内存对象，{@code @GlobalTransactional}
 * 的落点在用例入口 {@code OrderApplicationService#create}（裁定 D4）——⚠ **不是**编排层方法入口：
 * 挂在那里会被 Seata 静默忽略（它按 {@code BeanDefinition.getBeanClassName()} 挑目标，装配类
 * {@code @Bean} 产出的 bean 类名恒空），见该类注释。</p>
 *
 * <p>⚠ <b>订正一句早期说法</b>：本类与 {@code OrderItem} 的注释曾写过「落库时只换适配器即可，
 * domain 一行都不用动」。那句话**是错的**，落库期（2026-09-21）实际动了三处，且都是必需的：
 * ① 收货地址进模型（{@link OrderAddress}）——地址是订单的事实，不属于适配器；
 * ② 快递单号进模型（{@link #markShipped}）——它是「发货」这个动作的产物，弃之则顾客永远看不到物流单号；
 * ③ 支付金额校验（{@link #markPaid}）——金额是订单自己的属性，校验只能在聚合里做。
 * 适配器能替换的只是**外部依赖**（仓库 / 商品 / 库存），订单**自己有什么**必须由聚合说完。</p>
 */
public final class OrderModel {

    /** 快递单号长度上限（= {@code trade_order.ship_no} 列宽） */
    public static final int MAX_TRACKING_NO_LENGTH = 64;

    private final String orderNo;
    private final Long customerId;
    private final Long storeId;
    private final String storeName;
    private final OrderSource source;
    /** 收货地址快照（下单当时；此后顾客改地址不影响已下的单） */
    private final OrderAddress address;
    /** 请求级幂等键（裁定 D6 第一级）；可为 null = 本次提交不做请求级去重 */
    private final String requestId;
    /** 订单指纹（裁定 D6 第二级）；由 {@link OrderFingerprint} 算出，不含金额与时间 */
    private final String fingerprint;
    private final LocalDateTime createTime;

    /** 订单行：开单时按 skuId 升序排好（确定性——同一批入参无论顺序如何，模型内部状态一致） */
    private final List<OrderItem> items;

    /** 状态轨迹：初始只有 {@link OrderStatus#PENDING_PAYMENT}，每次成功迁移追加一个（裁定 D16） */
    private final List<OrderStatus> statusTrail = new ArrayList<>();

    private OrderStatus status;
    private int totalQuantity;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    /** 快递单号：发货时录入，此前为 null。⚠ 它是**业务列**，与审计字段无关 */
    private String shipNo;
    private boolean sealed;

    private OrderModel(String orderNo,
                       Long customerId,
                       Long storeId,
                       String storeName,
                       OrderSource source,
                       OrderAddress address,
                       String requestId,
                       String fingerprint,
                       LocalDateTime createTime,
                       List<OrderItem> items) {
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.storeId = storeId;
        this.storeName = storeName;
        this.source = source;
        this.address = address;
        this.requestId = requestId;
        this.fingerprint = fingerprint;
        this.createTime = createTime;
        this.items = List.copyOf(items);
        // 订单一旦开出来就是「待支付」（todo：订单创建完成时将订单状态设置为待支付）
        this.status = OrderStatus.PENDING_PAYMENT;
        this.statusTrail.add(OrderStatus.PENDING_PAYMENT);
        refreshTotals();
    }

    /**
     * 开一笔订单（两段式生命周期的第一段）：只收 id + 数量，商品快照与金额待步骤补全
     *
     * <p>⚠ {@code storeName} 由**调用方（拆单阶段）**传入，不由步骤填：店铺名来自商品快照，而快照是
     * 按 SKU 取的——一单一店意味着同一个店铺名会在多行上重复出现，让步骤去填它会引入「多行谁说了算」
     * 的歧义；拆单时既然已经按 storeId 分好组，店铺名天然是那一组的一个常量。</p>
     *
     * <p>⚠ 入参为空行列表**不在这里拒绝**：空订单在 seal 时才会被拦下（{@link #assertCompletable}）。
     * 这不是疏漏——「步骤没把行补全」与「调用方就是没给行」在这里无法区分，让 seal 统一兜住，
     * 才使得这条不变量有唯一的落点、也可被测试。</p>
     *
     * @param orderNo     业务可读单号（由编排层生成并查重，模型不认识生成规则）
     * @param customerId  顾客 id（锚点，来自 BFF 的登录态）
     * @param storeId     店铺 id（拆单的键，一单一店）
     * @param storeName   店铺名（拆单阶段传入）
     * @param source      订单来源（详情页直购 / 购物车结算）
     * @param address     收货地址快照（**必填**：没有收货地址的订单不成立，见 {@link OrderAddress}）
     * @param requestId   请求级幂等键，可为 null
     * @param fingerprint 订单指纹（{@link OrderFingerprint#of} 算出）
     * @param createTime  下单时刻（由编排层传入，模型不自己取时间 → 单测可预测）
     * @param lines       订单行（只有 skuId + 数量）
     * @return 未 seal 的订单，等待步骤链补全
     * @throws IllegalArgumentException 同一 skuId 出现多次，或数量越界（{@code 1..999}）
     */
    public static OrderModel open(String orderNo,
                                  Long customerId,
                                  Long storeId,
                                  String storeName,
                                  OrderSource source,
                                  OrderAddress address,
                                  String requestId,
                                  String fingerprint,
                                  LocalDateTime createTime,
                                  List<OrderLine> lines) {
        Objects.requireNonNull(orderNo, "订单号不能为空");
        Objects.requireNonNull(customerId, "顾客 id 不能为空");
        Objects.requireNonNull(storeId, "店铺 id 不能为空");
        Objects.requireNonNull(source, "订单来源不能为空");
        Objects.requireNonNull(address, "收货地址不能为空");
        Objects.requireNonNull(createTime, "下单时间不能为空");

        List<OrderLine> incoming = lines == null ? List.of() : List.copyOf(lines);
        List<OrderItem> opened = new ArrayList<>(incoming.size());
        Set<Long> seenSkuIds = new HashSet<>(incoming.size());
        for (OrderLine line : incoming) {
            Objects.requireNonNull(line, "订单行不能为空");
            // ⚠ 同一 skuId 只允许一行（合并是**调用方**的职责，裁定 D14，由 OrderCreateCommand 完成）：
            //    聚合里出现两行同 skuId，applyPrice(skuId, ...) 就不知道指哪一行，
            //    后续按 skuId 找行的方法会静默只改一行——这正是「不报错但写坏数据」，故在这里直接拒绝。
            if (!seenSkuIds.add(line.skuId())) {
                throw new IllegalArgumentException("同一 skuId 在同一笔订单里出现多次：skuId=" + line.skuId() + "（入参应先按 skuId 合并数量）");
            }
            opened.add(OrderItem.open(line.skuId(), line.quantity()));
        }
        opened.sort(Comparator.comparing(OrderItem::getSkuId));
        return new OrderModel(orderNo, customerId, storeId, storeName, source, address, requestId, fingerprint, createTime, opened);
    }

    /**
     * 从落库状态重建一笔**已封存**的订单（⚠ 只有仓储适配器该调它）
     *
     * <p>它与 {@link #open} 的方向相反：{@code open} 建一个待补全的壳，本方法直接给出终态
     * （{@code sealed=true}、订单行已补全、轨迹与状态来自落库值）。两者共享同一套不变量，
     * 但**校验的来源不同**：{@code open} 校验入参，本方法校验**读出来的数据自洽**——
     * 库里读到的东西也可能错（列串位、轨迹被截断、状态与单号不匹配），
     * 这类错误不在这里挡住，就会一路带到页面上。</p>
     *
     * <p>断言四条（任一不满足即 {@link IllegalStateException}，都是「数据被写坏」而不是用户输入问题）：</p>
     * <ol>
     *   <li>订单行非空、按 skuId 严格升序且不重复（顺序错说明读出来的行串了）；</li>
     *   <li>轨迹非空、首项是状态机的初始状态、且每一步都是「下标 +1」（轨迹被截断或跳级都拦下）；</li>
     *   <li>快递单号与轨迹一致：轨迹里出现过「已发货」⟺ 单号非空（发货了没单号、没发货有单号都拦下）；</li>
     *   <li>总件数与总金额由订单行重算（{@code refreshTotals}），落库值与它的一致性由适配器对账
     *      ——那两列在库里，聚合只认它自己的行。</li>
     * </ol>
     *
     * @param orderNo     业务可读单号
     * @param customerId  顾客 id
     * @param storeId     店铺 id
     * @param storeName   店铺名快照
     * @param source      订单来源
     * @param address     收货地址快照
     * @param requestId   首次创建这笔单的请求号（可为 null）
     * @param fingerprint 订单指纹
     * @param shipNo      快递单号（未发货为 null）
     * @param createTime  下单时刻
     * @param items       订单行（已补全；按 skuId 升序）
     * @param statusTrail 状态轨迹（按 {@code seq} 升序的完整轨迹，含初始状态）
     * @param flow        状态机（用于校验轨迹是否是一条合法的「下标 +1」路径）
     * @return 已封存的订单
     * @throws IllegalStateException 上面四条断言任一不满足
     */
    public static OrderModel rehydrate(String orderNo,
                                       Long customerId,
                                       Long storeId,
                                       String storeName,
                                       OrderSource source,
                                       OrderAddress address,
                                       String requestId,
                                       String fingerprint,
                                       String shipNo,
                                       LocalDateTime createTime,
                                       List<OrderItem> items,
                                       List<OrderStatus> statusTrail,
                                       OrderStatusFlow flow) {
        Objects.requireNonNull(orderNo, "订单号不能为空");
        Objects.requireNonNull(customerId, "顾客 id 不能为空");
        Objects.requireNonNull(storeId, "店铺 id 不能为空");
        Objects.requireNonNull(source, "订单来源不能为空");
        Objects.requireNonNull(address, "收货地址不能为空");
        Objects.requireNonNull(createTime, "下单时间不能为空");
        Objects.requireNonNull(flow, "订单状态机不能为空");
        requireReadableItems(orderNo, items);
        requireReadableTrail(orderNo, statusTrail, flow);
        String trackingNo = trimToNull(shipNo);
        if (trackingNo != null && trackingNo.length() > MAX_TRACKING_NO_LENGTH) {
            throw new IllegalStateException("订单 " + orderNo + " 落库的快递单号超过 "
                    + MAX_TRACKING_NO_LENGTH + " 个字符，不能重建");
        }
        boolean shipped = statusTrail.contains(OrderStatus.SHIPPED);
        if (shipped != (trackingNo != null)) {
            throw new IllegalStateException("订单 " + orderNo + " 的快递单号与状态轨迹不一致（轨迹含「已发货」="
                    + shipped + "，单号=" + (trackingNo == null ? "null" : trackingNo) + "）");
        }

        OrderModel order = new OrderModel(orderNo, customerId, storeId, storeName, source, address,
                requestId, fingerprint, createTime, items);
        order.statusTrail.clear();
        order.statusTrail.addAll(statusTrail);
        order.status = statusTrail.get(statusTrail.size() - 1);
        order.shipNo = trackingNo;
        // 落库的订单一律是封存过的：没有「读了半成品出来」这种状态，重建出来即可迁移状态
        order.sealed = true;
        return order;
    }

    /**
     * 补全某一行的商品快照（goods-check 步骤调用）
     *
     * @param skuId    目标订单行的 skuId
     * @param snapshot 商品快照
     * @throws IllegalStateException    模型已 seal（已下单的订单不允许再改行）
     * @throws IllegalArgumentException 找不到该 skuId 对应的订单行
     */
    public void applyGoodsSnapshot(Long skuId, SkuSnapshot snapshot) {
        assertNotSealed();
        requireItem(skuId).fulfill(snapshot);
    }

    /**
     * 补全某一行的单价并重算小计（price-compute 步骤调用）
     *
     * <p>每次调用后重算总件数与总金额并缓存：总额是「快照 + 单价都齐了」的派生量，
     * 分次补价时它会先偏小，直到 seal 时与行求和对账——这是设计内的中间态，
     * 因为未 seal 的模型本来就不允许被当成订单使用。</p>
     *
     * @param skuId     目标订单行的 skuId
     * @param unitPrice 商品单价，{@code >= 0.01}
     * @throws IllegalStateException    模型已 seal，或该行尚未补全商品快照（顺序错了）
     * @throws IllegalArgumentException 找不到该 skuId 对应的订单行，或单价低于 {@code 0.01}
     */
    public void applyPrice(Long skuId, BigDecimal unitPrice) {
        assertNotSealed();
        requireItem(skuId).price(unitPrice);
        refreshTotals();
    }

    /**
     * 封存：断言模型可成单后冻结，此后不再允许改行
     *
     * <p>断言四条（任一不满足即 {@link IllegalStateException}，都是编程/装配错误而非用户输入问题）：
     * 至少一行、每行快照与单价齐备、总件数等于行件数之和、总金额等于行小计之和。</p>
     *
     * <p>⚠ 重复调用是**幂等**的（已 seal 时状态不再变化，断言依然成立后直接返回）：步骤重跑、
     * 或者将来「下单重试」这类路径不该被一个一次性闸门卡住，真正的闸门是「seal 后不许改行」。</p>
     *
     * @throws IllegalStateException 模型不完整（缺行 / 缺快照 / 缺单价 / 金额对不上）
     */
    public void seal() {
        assertCompletable();
        this.sealed = true;
    }

    /**
     * 迁移到目标状态（经状态机校验；只能「下标 +1」地前进）
     *
     * <p>⚠ <b>未 seal 的模型不允许迁移</b>：一笔还没补全的订单不能是「待支付」——它此刻既没有完整金额
     * 也没有商品快照，把它置成待支付等于把一个半成品交给用户。</p>
     *
     * <p>⚠ <b>seal 之后迁移照常允许</b>：封存冻结的是「订单行与金额」，不是状态——已下单的订单当然要能发货。
     * 两条规则不矛盾：一条防的是「拿半成品当订单」，一条是订单的正常生命期。</p>
     *
     * @param target 目标状态
     * @param flow   状态机（顺序来自配置，裁定 D7）
     * @throws IllegalStateException 模型尚未 seal
     * @throws com.panoramic.common.exception.ServiceException 非法迁移（跳级 / 回退 / 重复），HTTP 400
     */
    public void transitionTo(OrderStatus target, OrderStatusFlow flow) {
        Objects.requireNonNull(flow, "订单状态机不能为空");
        assertSealed();
        flow.transition(this, target);
    }

    /**
     * 置为已支付（付款成功后调用），同时校验支付金额
     *
     * <p>⚠ <b>金额校验只能在聚合力做</b>：它是「实付 vs 订单总额」的比对，而订单总额是封存时冻结在
     * 聚合里的值。放到应用层或页面层，就会出现「谁都能改一下支付金额」的入口，
     * 以及第二份「什么叫付对了」的口径。</p>
     *
     * <p>⚠ 校验**先于**状态迁移：金额不对时不该留下任何状态副作用（更不该走到库里）。
     * 金额相等用 {@code compareTo} 而不是 {@code equals}——BigDecimal 的 {@code equals} 把书写形式
     * （scale）也算进相等性，{@code 20.0} 与 {@code 20.00} 是同一笔钱。</p>
     *
     * @param flow       状态机
     * @param paidAmount 本次支付金额（顾客输入）
     * @throws ServiceException 金额与订单总额不一致（HTTP 400，提示语可直接展示给顾客）
     */
    public void markPaid(OrderStatusFlow flow, BigDecimal paidAmount) {
        Objects.requireNonNull(paidAmount, "支付金额不能为空");
        if (paidAmount.compareTo(totalAmount) != 0) {
            throw new ServiceException(400, "支付金额与订单总额不一致（应付 " + totalAmount.toPlainString()
                    + " 元，实付 " + paidAmount.toPlainString() + " 元）");
        }
        transitionTo(OrderStatus.PAID, flow);
    }

    /**
     * 置为已发货（店主发货后调用），并记下快递单号
     *
     * <p>⚠ 入参校验 → 状态迁移 → 记单号，**顺序不可颠倒**：单号为空、或状态机拒绝这次迁移
     * （重复发货 / 跳级）时，{@code shipNo} 必须保持原样。先记后迁移的话，一次被拒的发货会在这笔单上
     * 留下一个「没发货却有了单号」的中间态——事务回滚能救库里的数据，救不了手里这个对象，
     * 而它正是接下来要被拿去落库的那个。</p>
     *
     * @param flow       状态机
     * @param trackingNo 快递单号（必填）
     * @throws ServiceException 单号为空或超长（HTTP 400），或状态机拒绝本次迁移（重复发货 / 跳级）
     */
    public void markShipped(OrderStatusFlow flow, String trackingNo) {
        String normalized = trimToNull(trackingNo);
        if (normalized == null) {
            throw new ServiceException(400, "发货必须填写快递单号");
        }
        if (normalized.length() > MAX_TRACKING_NO_LENGTH) {
            throw new ServiceException(400, "快递单号不能超过 " + MAX_TRACKING_NO_LENGTH + " 个字符");
        }
        transitionTo(OrderStatus.SHIPPED, flow);
        this.shipNo = normalized;
    }

    /**
     * 置为已收货（顾客确认收货后调用；本状态的店主侧文案是「完成」）
     *
     * @param flow 状态机
     */
    public void markReceived(OrderStatusFlow flow) {
        transitionTo(OrderStatus.RECEIVED, flow);
    }

    /**
     * 改状态并留下轨迹（**包内可见**：唯一合法的调用者是同包的 {@link OrderStatusFlow}）
     *
     * <p>⚠ seal 检查放在这里而不是只放在 {@link #transitionTo}：本方法是所有状态变更的**唯一**写入口，
     * 从 {@link OrderStatusFlow#transition} 直接进来（测试、将来的其它调用方）也必须得到同一种异常，
     * 不能因为「走哪个入口」而给出不同的错误类型。</p>
     *
     * @param target 目标状态
     * @throws IllegalStateException 模型尚未 seal
     */
    void applyStatus(OrderStatus target) {
        Objects.requireNonNull(target, "目标订单状态不能为空");
        assertSealed();
        this.status = target;
        this.statusTrail.add(target);
    }

    /**
     * @return 业务可读单号
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * @return 顾客 id（数据锚点，来自 BFF 的登录态）
     */
    public Long getCustomerId() {
        return customerId;
    }

    /**
     * @return 店铺 id（拆单的键）
     */
    public Long getStoreId() {
        return storeId;
    }

    /**
     * @return 店铺名（拆单阶段由调用方传入）
     */
    public String getStoreName() {
        return storeName;
    }

    /**
     * @return 订单来源（详情页直购 / 购物车结算）
     */
    public OrderSource getSource() {
        return source;
    }

    /**
     * @return 收货地址快照（下单当时；不可变值对象）
     */
    public OrderAddress getAddress() {
        return address;
    }

    /**
     * @return 快递单号；未发货前为 {@code null}
     */
    public String getShipNo() {
        return shipNo;
    }

    /**
     * @return 请求级幂等键；未传时为 {@code null}
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * @return 订单指纹（不含金额与时间，故同一次重复提交必然算出同一个值）
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * @return 当前状态
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * @return 订单行（**不可变副本**，按 skuId 升序；外部改不动聚合内部）
     */
    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    /**
     * @return 总件数 = Σ 行数量（开单后即有值，数量不依赖商品快照）
     */
    public int getTotalQuantity() {
        return totalQuantity;
    }

    /**
     * @return 总金额 = Σ 行小计（两位小数）；未 seal 时是**中间态**，以 seal 时的对账为准
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * @return 下单时刻（由编排层传入，模型不取系统时间）
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * @return 状态轨迹（**不可变副本**，含初始状态；「只前不退」在数据上的证据，裁定 D16）
     */
    public List<OrderStatus> getStatusTrail() {
        return List.copyOf(statusTrail);
    }

    /**
     * @return 是否已封存（封存后订单行与金额冻结，但状态仍可迁移）
     */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * 断言模型可成单（seal 的门槛）
     *
     * <p>消息里带上具体哪一行、差什么，是因为这四条都只会在**装配错误**时触发：
     * 把「哪一行没齐」写进消息，才能一眼看出是哪个步骤没跑。</p>
     */
    private void assertCompletable() {
        if (items.isEmpty()) {
            throw new IllegalStateException("订单 " + orderNo + " 没有任何订单行，不能封存");
        }
        for (OrderItem item : items) {
            if (!item.isCompleted()) {
                throw new IllegalStateException("订单项 skuId=" + item.getSkuId()
                        + " 的商品快照或单价尚未补全（商品快照已补全=" + item.isFulfilled()
                        + "，已定价=" + item.isPriced() + "），不能封存");
            }
        }
        int sumQuantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        if (sumQuantity != totalQuantity) {
            throw new IllegalStateException("订单 " + orderNo + " 的总件数(" + totalQuantity
                    + ")与订单行件数之和(" + sumQuantity + ")不一致，不能封存");
        }
        BigDecimal sumSubtotal = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        // ⚠ 用 compareTo 而不是 equals：BigDecimal 的 equals 把 scale 也算进相等性（0.00 != 0），
        //    金额相等是**数值**相等，不是书写形式相等。
        if (sumSubtotal.compareTo(totalAmount) != 0) {
            throw new IllegalStateException("订单 " + orderNo + " 的总金额(" + totalAmount.toPlainString()
                    + ")与订单行小计之和(" + sumSubtotal.toPlainString() + ")不一致，不能封存");
        }
    }

    /**
     * 重算总件数与总金额并缓存
     */
    private void refreshTotals() {
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            quantity += item.getQuantity();
            if (item.getSubtotal() != null) {
                amount = amount.add(item.getSubtotal());
            }
        }
        this.totalQuantity = quantity;
        this.totalAmount = amount;
    }

    /**
     * 校验从库里读出来的订单行自洽（{@link #rehydrate} 用）
     *
     * <p>三条：非空、每行都已补全、按 skuId **严格升序**（升序是模型的既有不变量，重复或乱序
     * 说明读出来的行不是这一单的，或者读的过程中串了位）。</p>
     */
    private static void requireReadableItems(String orderNo, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("订单 " + orderNo + " 落库的订单行为空，不能重建");
        }
        Long previousSkuId = null;
        for (OrderItem item : items) {
            Objects.requireNonNull(item, "订单行不能为空");
            if (!item.isCompleted()) {
                throw new IllegalStateException("订单 " + orderNo + " 落库的订单行 skuId=" + item.getSkuId()
                        + " 未补全（商品快照已补全=" + item.isFulfilled() + "，已定价=" + item.isPriced() + "），不能重建");
            }
            if (previousSkuId != null && item.getSkuId() <= previousSkuId) {
                throw new IllegalStateException("订单 " + orderNo + " 落库的订单行未按 skuId 升序（skuId="
                        + item.getSkuId() + " 出现在 " + previousSkuId + " 之后），不能重建");
            }
            previousSkuId = item.getSkuId();
        }
    }

    /**
     * 校验从库里读出来的状态轨迹是一条合法的路径（{@link #rehydrate} 用）
     *
     * <p>轨迹是「只前不退」在数据上的证据（裁定 D16），故它必须与状态机的配置对得上：
     * 首项是配置里的初始状态，之后每一步都是「下标 +1」。跳级 / 回退 / 被截断的轨迹都拦在这里
     * ——否则一笔数据被改坏的单会带着一条假轨迹继续流转。</p>
     */
    private static void requireReadableTrail(String orderNo, List<OrderStatus> trail, OrderStatusFlow flow) {
        if (trail == null || trail.isEmpty()) {
            throw new IllegalStateException("订单 " + orderNo + " 落库的状态轨迹为空，不能重建");
        }
        List<OrderStatus> configured = flow.statuses();
        if (trail.get(0) != configured.get(0)) {
            throw new IllegalStateException("订单 " + orderNo + " 落库的状态轨迹不是从初始状态开始的（首项="
                    + trail.get(0) + "，配置的初始状态=" + configured.get(0) + "）");
        }
        for (int i = 1; i < trail.size(); i++) {
            OrderStatus from = trail.get(i - 1);
            OrderStatus to = trail.get(i);
            if (to == null || from == null || configured.indexOf(to) != configured.indexOf(from) + 1) {
                throw new IllegalStateException("订单 " + orderNo + " 落库的状态轨迹不是合法的「下标 +1」路径："
                        + "第 " + (i + 1) + " 项是 " + (to == null ? "null" : to.name())
                        + "，它前面是 " + (from == null ? "null" : from.name()));
            }
        }
    }

    /**
     * 去首尾空白，空白串归一成 {@code null}
     *
     * <p>空白串与 null 在业务上都表示「没有」：把它们当成两种值会让「单号为空」这件事有两个判据，
     * 而库里存一个空格与存 NULL 在页面上的区别没人说得清。</p>
     */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 按 skuId 找订单行
     *
     * @throws IllegalArgumentException 找不到（调用方把 skuId 传错了，属编程错误；静默忽略会让
     *                                  「这一步白跑」与「这一步成功」长得一模一样）
     */
    private OrderItem requireItem(Long skuId) {
        Objects.requireNonNull(skuId, "skuId 不能为空");
        return items.stream()
                .filter(item -> item.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("订单 " + orderNo + " 里没有 skuId=" + skuId + " 的订单行"));
    }

    /**
     * 改行入口的统一闸门（seal 之后订单行与金额都冻结）
     */
    private void assertNotSealed() {
        if (sealed) {
            throw new IllegalStateException("订单 " + orderNo + " 已封存（seal），不能再修改订单行");
        }
    }

    /**
     * 状态迁移入口的统一闸门（未 seal 的模型不能被当成订单使用）
     */
    private void assertSealed() {
        if (!sealed) {
            throw new IllegalStateException("订单 " + orderNo + " 尚未封存（seal），不能迁移状态");
        }
    }
}
