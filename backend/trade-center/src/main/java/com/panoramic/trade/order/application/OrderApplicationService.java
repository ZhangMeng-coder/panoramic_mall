package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.trade.dto.TradeOrderAddressDTO;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderPlatformPageQueryDTO;
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
import com.panoramic.trade.order.domain.port.PlatformOrderQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.StringJoiner;

/**
 * 订单用例入口（三侧：顾客 / 商户 / 平台）：下单、分页、详情、支付、发货、收货。
 *
 * <p>控制器（{@code controller/OrderController}）只做参数绑定与校验，其余全在这里；
 * 编排（拆单 / 两级幂等 / 失败回滚）在 {@link OrderCreateCoordinator}，本类**不重复它的逻辑**——
 * 它只把契约入参翻译成域入参，再把域模型翻译成契约出参。</p>
 *
 * <h3>三侧读都经仓库的锚点方法，域内不做身份判断</h3>
 * <p>顾客 / 商户两侧把 {@code customerId} / {@code storeId} 传进仓库的查询，锚点**参与收窄**；
 * 平台侧没有锚点（管理端本就是全量视角），它要筛的店铺 / 顾客走 {@link PlatformOrderQuery} 的字段。
 * ⚠ 「调用方传的锚点是否真是本人 / 本店」由端 BFF 从登录态取，**域侧不校验**——防线在 BFF。
 * 详情 / 动作查询一律返回同一种 404「订单不存在」：**不区分「不存在」与「不属你」**
 * （区分开就等于告诉调用方「这笔单存在，只是不是你的」）。</p>
 *
 * <h3>为什么三个动作带事务、下单不带、六个读也带</h3>
 * <p>下单的事务边界在编排器方法上（先占键与订单必须同事务，见
 * {@link OrderRepository} 的接口注释），本类再声明一次只是重复。<br>
 * 三个动作则必须自己带：它们是「读订单 → 聚合内迁移状态 → 落库」三步，而落库要写
 * <b>订单行 + 状态轨迹两处</b>（{@link OrderRepository#update}），任何一步失败都得整体回退，
 * 否则会留下「状态列已改、轨迹没跟上」这类自相矛盾的单（列表按状态列、详情按轨迹，同单两个状态）。<br>
 * ⚠ 六个读路径（三侧各「分页 + 详情」）也声明 {@code readOnly} 事务——**不是为了回滚，是为了一个读视图**：
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

    // ── 顾客侧 ──────────────────────────────────────────────────────────────────

    /**
     * 下单（一次提交可能拆成多笔，一单一店；幂等命中时原样返回首次那批）
     *
     * @param customerId 顾客 id（数据权限锚点，由 mall-bff 从登录态取）
     * @param dto        下单参数（来源 / 幂等键 / 地址快照 / 商品行）
     * @return 本次提交的整批订单（顺序 = {@code storeId} 升序）
     */
    public List<TradeOrderVO> create(Long customerId, TradeOrderCreateDTO dto) {
        OrderCreateCommand command = new OrderCreateCommand(customerId,
                parseSource(dto.getSource()),
                toAddress(dto.getAddress()),
                dto.getRequestId(),
                dto.getItems().stream()
                        .map(item -> new OrderCreateCommand.Line(item.getSkuId(), item.getQuantity()))
                        .toList());
        return orderCreateCoordinator.create(command).stream().map(OrderApplicationService::toVo).toList();
    }

    /**
     * 我的订单分页（只含该顾客的订单；下单倒序）
     */
    @Transactional(readOnly = true)
    public TradeOrderPageVO pageCustomerOrders(Long customerId, TradeOrderPageQueryDTO dto) {
        return toPageVo(orderRepository.pageCustomerOrders(customerId, toQuery(dto)));
    }

    /**
     * 我的订单详情
     *
     * @throws ServiceException 订单不存在或不属于该顾客（HTTP 404，两种情况同一个提示）
     */
    @Transactional(readOnly = true)
    public TradeOrderVO getCustomerOrder(Long customerId, String orderNo) {
        return toVo(requireCustomerOrder(customerId, orderNo));
    }

    /**
     * 支付（假支付；金额校验在聚合内，不一致即 400）
     *
     * @throws ServiceException 金额不符 / 非法迁移（HTTP 400）、订单不属本人（404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long customerId, String orderNo, TradeOrderPayDTO dto) {
        OrderModel order = requireCustomerOrder(customerId, orderNo);
        // 校验先于迁移，迁移先于落库：任何一处抛出都不写库（见 OrderModel#markPaid 的说明）
        order.markPaid(orderStatusFlow, dto.getAmount());
        orderRepository.update(order);
    }

    /**
     * 确认收货（终态）
     *
     * @throws ServiceException 非法迁移（HTTP 400）、订单不属本人（404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(Long customerId, String orderNo) {
        OrderModel order = requireCustomerOrder(customerId, orderNo);
        order.markReceived(orderStatusFlow);
        orderRepository.update(order);
    }

    // ── 商户侧 ──────────────────────────────────────────────────────────────────

    /**
     * 本店订单分页（**含待支付**：店主的待办起点不是「已支付」，见 {@link OrderRepository#pageStoreOrders}）
     */
    @Transactional(readOnly = true)
    public TradeOrderPageVO pageStoreOrders(Long storeId, TradeOrderPageQueryDTO dto) {
        return toPageVo(orderRepository.pageStoreOrders(storeId, toQuery(dto)));
    }

    /**
     * 本店订单详情
     *
     * @throws ServiceException 订单不存在或不属于该店铺（HTTP 404，两种情况同一个提示）
     */
    @Transactional(readOnly = true)
    public TradeOrderVO getStoreOrder(Long storeId, String orderNo) {
        return toVo(requireStoreOrder(storeId, orderNo));
    }

    /**
     * 发货（记录快递单号；单号校验与状态迁移都在聚合内）
     *
     * @throws ServiceException 单号为空或超长 / 非法迁移（HTTP 400）、订单不属本店（404）
     */
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Long storeId, String orderNo, TradeOrderShipDTO dto) {
        OrderModel order = requireStoreOrder(storeId, orderNo);
        order.markShipped(orderStatusFlow, dto.getTrackingNo());
        orderRepository.update(order);
    }

    // ── 平台侧（无锚点） ────────────────────────────────────────────────────────

    /**
     * 平台侧订单分页（全量视角；按店铺 / 顾客筛是**可选筛选**，不是锚点）
     */
    @Transactional(readOnly = true)
    public TradeOrderPageVO pagePlatformOrders(TradeOrderPlatformPageQueryDTO dto) {
        PlatformOrderQuery query = new PlatformOrderQuery(dto.getOrderNo(), parseStatus(dto.getStatus()),
                dto.getStoreId(), dto.getCustomerId(), dto.getPageNum(), dto.getPageSize());
        return toPageVo(orderRepository.pagePlatformOrders(query));
    }

    /**
     * 平台侧订单详情（全量视角）
     *
     * @throws ServiceException 订单不存在（HTTP 404）
     */
    @Transactional(readOnly = true)
    public TradeOrderVO getPlatformOrder(String orderNo) {
        return toVo(orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ServiceException(404, "订单不存在")));
    }

    // ── 内部：按锚点取单（取不到一律 404） ──────────────────────────────────────

    private OrderModel requireCustomerOrder(Long customerId, String orderNo) {
        return orderRepository.findByCustomerOrderNo(customerId, orderNo)
                .orElseThrow(() -> new ServiceException(404, "订单不存在"));
    }

    private OrderModel requireStoreOrder(Long storeId, String orderNo) {
        return orderRepository.findByStoreOrderNo(storeId, orderNo)
                .orElseThrow(() -> new ServiceException(404, "订单不存在"));
    }

    // ── 内部：契约入参 → 域入参 ─────────────────────────────────────────────────

    /**
     * 分页查询条件（顾客 / 商户侧：**没有锚点字段**，锚点是方法入参）
     */
    private static OrderQuery toQuery(TradeOrderPageQueryDTO dto) {
        return new OrderQuery(dto.getOrderNo(), parseStatus(dto.getStatus()), dto.getPageNum(), dto.getPageSize());
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
