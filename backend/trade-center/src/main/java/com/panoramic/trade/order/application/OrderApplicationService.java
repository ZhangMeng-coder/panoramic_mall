package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.trade.dto.TradeOrderAddressDTO;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderReceiveDTO;
import com.panoramic.contract.trade.dto.TradeOrderShipDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.trade.order.domain.OrderAddress;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.OrderSource;
import com.panoramic.trade.order.domain.OrderStatus;
import com.panoramic.trade.order.domain.OrderStatusFlow;
import com.panoramic.trade.order.domain.port.OrderPage;
import com.panoramic.trade.order.domain.port.OrderQuery;
import com.panoramic.trade.order.domain.port.OrderRepository;
import com.panoramic.trade.support.ScopeGuard;
import lombok.RequiredArgsConstructor;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.StringJoiner;

/**
 * 订单用例入口（按能力六条：下单、分页、详情、支付、发货、收货）：{@code create} / {@code pageOrders} /
 * {@code getOrder} / {@code payOrder} / {@code shipOrder} / {@code receiveOrder}。
 *
 * <p>控制器（{@code controller/OrderController}）只做参数绑定与校验，其余全在这里；
 * 编排（拆单 / 两级幂等 / 失败回滚）在 {@link OrderCreateCoordinator}，本类**不重复它的逻辑**——
 * 它只把契约入参翻译成域入参，再把域模型翻译成契约出参。</p>
 *
 * <h3>读侧一条路径，作用域由调用方传进来，域内不做身份判断</h3>
 * <p>分页与详情各只有一条路径（{@link OrderRepository#pageOrders} / {@link OrderRepository#findOrder}），
 * 作用域（{@code customerId} / {@code storeId}）是 {@link OrderQuery} 里的**可选字段**：
 * 传了就按它收窄、没传就是不限定（管理端全量视角）。⚠ 值「是否真是本人 / 本店」由端 BFF 从登录态取，
 * **域侧不校验、也不判 {@code X-User-Type}**——防线在 BFF。
 * 详情 / 动作查询一律返回同一种 404「订单不存在」：**不区分「不存在」与「不属你」**
 * （区分开就等于告诉调用方「这笔单存在，只是不是你的」）。</p>
 *
 * <p>⚠ <b>写侧的作用域必填，两道防线</b>（{@code customerId} / {@code storeId}）：写没有「合法全量视角」，
 * 省掉作用域就是「能改任意一笔单」。第一道是各入参 DTO 上的 {@code @NotNull}（只覆盖 MVC 边界）；
 * 第二道是本类四个写方法开头的 {@link ScopeGuard#require}——绕过 MVC 的调用（内部直连 / 单测 / 将来的批处理）
 * 只有它能挡，否则 {@code null} 会一路传到仓储、退化成「不限定」并**静默**改掉别人的单。
 * ⚠ 这道护栏是**入参不变量**（参数在不在），不是鉴权（是不是你的单）——后者域内一律不做。</p>
 *
 * <h3>为什么下单挂全局事务、三个动作各自带事务、两个读也带</h3>
 * <p><b>下单挂的是全局事务（{@code @GlobalTransactional}，Seata TM 侧）</b>——它要**跨服务写库**：
 * 库存的扣减与回补落在 store 域（分支事务），本地回滚补不回来。
 * ⚠ 它**必须落在本类、不能挂在编排器入口**：Seata 的 {@code GlobalTransactionScanner} 按
 * <b>{@code BeanDefinition.getBeanClassName()}</b> 挑要增强的 bean，**取不到类名即跳过**；而编排器
 * {@link OrderCreateCoordinator} 由装配类（{@code OrderDomainConfiguration}）的 {@code @Bean} 方法产出、
 * 类名**恒为空**，注解挂在那里会被**静默忽略**（不报错、不告警，事务根本不开）。本类是组件扫描出来的
 * {@code @Service}，类名非空，注解才真的生效；落点由单测 {@code GlobalTransactionalPlacementTest} 守着
 * （按「扫描到的组件里至少有一个方法带 {@code @GlobalTransactional}」断言，防的就是这种「挪个位置就静默失效」）。<br>
 * 库里那一半（先占键 + 订单落库）的本地事务边界仍在 {@link OrderCreateCoordinator#create}
 * （先占键与订单必须同事务，见 {@link OrderRepository} 的接口注释）——两者**分工不同、并存而非互相替代**，
 * 去掉本地那行并不会被全局事务补上。⚠ 一批全是复用笔时没有跨服务写，全局事务里只剩 trade-center
 * 自己那一个分支（AT 的数据源代理由 {@code seata.yml} 的 {@code enable-auto-data-source-proxy} 开着），
 * 它不比本地事务多保护任何东西。<br>
 * 三个动作则必须自己带：它们是「读订单 → 聚合内迁移状态 → 落库」三步，而落库要写
 * <b>订单行 + 状态轨迹两处</b>（{@link OrderRepository#update}），任何一步失败都得整体回退，
 * 否则会留下「状态列已改、轨迹没跟上」这类自相矛盾的单（列表按状态列、详情按轨迹，同单两个状态）。<br>
 * ⚠ 两个读路径（分页 + 详情）也声明 {@code readOnly} 事务——**不是为了回滚，是为了一个读视图**：
 * 仓库组装一笔订单要三次查询（订单行 → 明细 → 轨迹），而重建聚合时的对账正好横跨其中两处
 * （「快递单号 ⟺ 轨迹含已发货」跨第 1 与第 3 次，「总额 ⟺ 明细之和」跨第 1 与第 2 次）。
 * 三次查询若不在同一快照上，恰好插进一次发货，就会读到「单号还是空、轨迹已含已发货」而被判成对账失败：
 * 单本身完全合法，却报成 5xx（分页时一页内任一笔命中即整页失败）。只读事务让三次 SELECT 落在同一快照上，
 * 把并发写挡在视图之外。<br>
 * ⚠ 这一层防护的**前提是 MySQL 默认的 REPEATABLE READ**（快照在事务首次读时建立）：若把隔离级别调成
 * READ COMMITTED，每条 SELECT 各取新快照，窗口会重新打开。Nacos 的 `datasource-mysql.yml` 未设
 * `isolation`，走的就是默认值——改隔离级别时要一并回头看这里。</p>
 *
 * <h3>契约里是字符串、域里是枚举</h3>
 * <p>{@code source} / {@code status} 在契约 DTO 里是 {@code String}（枚举在域内，契约包不另立一份）。
 * 解析只认枚举常量名（{@link OrderStatus#valueOf}），**不做大小写折叠**：契约值就是枚举名，
 * 折叠出一个「也接受 paid」的隐性第二口径，等于把「哪些值算合法」变成两处说了算。
 * 解析失败按 400 回，**提示语由枚举常量拼出**（不在代码里手写一份取值清单——那也会漂移）。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderCreateCoordinator orderCreateCoordinator;
    private final OrderRepository orderRepository;
    private final OrderStatusFlow orderStatusFlow;

    // ── 写侧：作用域必填（在各自 DTO 上以 @NotNull 表达） ────────────────────────

    /**
     * 下单（一次提交可能拆成多笔，一单一店；幂等命中时原样返回首次那批）
     *
     * @param dto 下单参数（**作用域** customerId + 来源 / 幂等键 / 地址快照 / 商品行）
     * @return 本次提交的整批订单（顺序 = {@code storeId} 升序）
     */
    @GlobalTransactional
    public List<TradeOrderVO> create(TradeOrderCreateDTO dto) {
        ScopeGuard.require(dto.getCustomerId(), "顾客 id");
        OrderCreateCommand command = new OrderCreateCommand(dto.getCustomerId(),
                parseSource(dto.getSource()),
                toAddress(dto.getAddress()),
                dto.getRequestId(),
                dto.getItems().stream()
                        .map(item -> new OrderCreateCommand.Line(item.getSkuId(), item.getQuantity()))
                        .toList());
        return orderCreateCoordinator.create(command).stream().map(OrderApplicationService::toVo).toList();
    }

    /**
     * 支付（假支付；金额校验在聚合内，不一致即 400）
     *
     * @param orderNo 业务可读单号
     * @param dto     支付金额 + **作用域** customerId（必填）
     * @throws ServiceException 缺少作用域 / 金额不符 / 非法迁移（HTTP 400）、订单不属本人（404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(String orderNo, TradeOrderPayDTO dto) {
        ScopeGuard.require(dto.getCustomerId(), "顾客 id");
        OrderModel order = requireOrder(OrderQuery.forDetail(orderNo, dto.getCustomerId(), null));
        // 校验先于迁移，迁移先于落库：任何一处抛出都不写库（见 OrderModel#markPaid 的说明）
        order.markPaid(orderStatusFlow, dto.getAmount());
        orderRepository.update(order);
    }

    /**
     * 发货（记录快递单号；单号校验与状态迁移都在聚合内）
     *
     * @param orderNo 业务可读单号
     * @param dto     快递单号 + **作用域** storeId（必填）
     * @throws ServiceException 缺少作用域 / 单号为空或超长 / 非法迁移（HTTP 400）、订单不属本店（404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(String orderNo, TradeOrderShipDTO dto) {
        ScopeGuard.require(dto.getStoreId(), "店铺 id");
        OrderModel order = requireOrder(OrderQuery.forDetail(orderNo, null, dto.getStoreId()));
        order.markShipped(orderStatusFlow, dto.getTrackingNo());
        orderRepository.update(order);
    }

    /**
     * 确认收货（终态）
     *
     * @param orderNo 业务可读单号
     * @param dto     **作用域** customerId（必填）
     * @throws ServiceException 缺少作用域 / 非法迁移（HTTP 400）、订单不属本人（404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(String orderNo, TradeOrderReceiveDTO dto) {
        ScopeGuard.require(dto.getCustomerId(), "顾客 id");
        OrderModel order = requireOrder(OrderQuery.forDetail(orderNo, dto.getCustomerId(), null));
        order.markReceived(orderStatusFlow);
        orderRepository.update(order);
    }

    // ── 读侧：作用域可选（不传 = 全量视角） ─────────────────────────────────────

    /**
     * 订单分页（**同一能力对所有调用方**）：传 {@code customerId} 即「我的订单」、
     * 传 {@code storeId} 即「本店订单」（**含待支付**：店主的待办起点不是「已支付」）、
     * 都不传即全量；下单倒序
     */
    @Transactional(readOnly = true)
    public TradeOrderPageVO pageOrders(TradeOrderPageQueryDTO dto) {
        return toPageVo(orderRepository.pageOrders(toQuery(dto)));
    }

    /**
     * 订单详情（传了作用域就收窄到那一份；都不传即全量视角）
     *
     * @throws ServiceException 订单不存在或不在该作用域内（HTTP 404，两种情况同一个提示）
     */
    @Transactional(readOnly = true)
    public TradeOrderVO getOrder(String orderNo, TradeOrderQueryDTO dto) {
        return toVo(requireOrder(OrderQuery.forDetail(orderNo, dto.getCustomerId(), dto.getStoreId())));
    }

    // ── 内部：按作用域取单（取不到一律 404） ────────────────────────────────────

    /**
     * 取一笔订单；不存在或不在作用域内都抛同一个 404「订单不存在」（见类注释：不区分两种情形）
     */
    private OrderModel requireOrder(OrderQuery query) {
        return orderRepository.findOrder(query)
                .orElseThrow(() -> new ServiceException(404, "订单不存在"));
    }

    // ── 内部：契约入参 → 域入参 ─────────────────────────────────────────────────

    /**
     * 分页查询条件（四个可选条件一并交给域侧：作用域与筛选在域内是同一种「传了就筛」）
     */
    private static OrderQuery toQuery(TradeOrderPageQueryDTO dto) {
        return new OrderQuery(dto.getOrderNo(), parseStatus(dto.getStatus()),
                dto.getCustomerId(), dto.getStoreId(), dto.getPageNum(), dto.getPageSize());
    }

    /**
     * 地址快照（DTO → 域值对象）：长度与空白校验在 {@link OrderAddress} 的构造器里
     * （同一份口径，不在这里再写一遍）
     */
    private static OrderAddress toAddress(TradeOrderAddressDTO dto) {
        return new OrderAddress(dto.getReceiverName(), dto.getReceiverPhone(), dto.getRegion(), dto.getDetail());
    }

    /**
     * 解析订单来源（契约层是字符串，域枚举是唯一取值来源）
     *
     * @throws ServiceException 取值不是任何枚举常量名（HTTP 400）
     */
    private static OrderSource parseSource(String source) {
        if (source == null) {
            // @NotBlank 已在契约层挡住，这里只是不把 null 变成 NPE（那会以 500 出去）
            throw new ServiceException(400, "订单来源不能为空");
        }
        try {
            return OrderSource.valueOf(source.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, "订单来源取值非法：" + source + "（可选值：" + names(OrderSource.values()) + "）");
        }
    }

    /**
     * 解析订单状态（筛选条件）：空 / 空白串 = 不筛
     *
     * @throws ServiceException 取值不是任何枚举常量名（HTTP 400）
     */
    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, "订单状态取值非法：" + status + "（可选值：" + names(OrderStatus.values()) + "）");
        }
    }

    /**
     * 枚举常量名列表（拼错误提示用）
     *
     * <p>提示里的取值清单**从枚举现取**，不手写一份——手写的那份迟早与枚举对不上。</p>
     */
    private static String names(Enum<?>[] values) {
        StringJoiner joiner = new StringJoiner(" / ");
        for (Enum<?> value : values) {
            joiner.add(value.name());
        }
        return joiner.toString();
    }

    // ── 内部：域模型 → 契约出参 ─────────────────────────────────────────────────

    private static TradeOrderPageVO toPageVo(OrderPage page) {
        TradeOrderPageVO vo = new TradeOrderPageVO();
        vo.setTotal(page.total());
        vo.setRecords(page.records().stream().map(OrderApplicationService::toVo).toList());
        return vo;
    }

    /**
     * 订单 → 出参
     *
     * <p>两侧状态文案由域下发（{@code statusMallLabel} / {@code statusStoreAdminLabel}），
     * 端 BFF 不重写——同一份文案各写一遍必漂移。</p>
     */
    private static TradeOrderVO toVo(OrderModel order) {
        TradeOrderVO vo = new TradeOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setCustomerId(order.getCustomerId());
        vo.setStoreId(order.getStoreId());
        vo.setStoreName(order.getStoreName());
        vo.setSource(order.getSource().name());
        vo.setStatus(order.getStatus().name());
        vo.setStatusMallLabel(order.getStatus().getMallLabel());
        vo.setStatusStoreAdminLabel(order.getStatus().getStoreAdminLabel());
        vo.setTotalQuantity(order.getTotalQuantity());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setShipNo(order.getShipNo());
        vo.setCreateTime(order.getCreateTime());
        vo.setAddress(toAddressVo(order.getAddress()));
        vo.setItems(order.getItems().stream().map(OrderApplicationService::toItemVo).toList());
        return vo;
    }

    private static TradeOrderAddressDTO toAddressVo(OrderAddress address) {
        TradeOrderAddressDTO vo = new TradeOrderAddressDTO();
        vo.setReceiverName(address.receiverName());
        vo.setReceiverPhone(address.receiverPhone());
        vo.setRegion(address.region());
        vo.setDetail(address.detail());
        return vo;
    }

    /**
     * 订单行 → 出参（单价 / 小计 / 商品快照都是**下单当时**的值，来自聚合而非再查商品域）
     */
    private static TradeOrderVO.Item toItemVo(OrderItem item) {
        TradeOrderVO.Item vo = new TradeOrderVO.Item();
        vo.setSkuId(item.getSkuId());
        vo.setSpuId(item.getSpuId());
        vo.setGoodsName(item.getGoodsName());
        vo.setMainImage(item.getMainImage());
        vo.setSpecAttrs(item.getSpecAttrs());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }
}
