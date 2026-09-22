package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderShipDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.storebff.dto.StoreOrderPageQueryDTO;
import com.panoramic.storebff.dto.StoreOrderShipDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 店铺端 BFF · 商户侧订单编排（列表 / 详情 / 发货）。
 * <p>只做页面编排，不持有/复制 trade-center 的任何实体与表：订单数据经
 * {@link TradeCenterClient} 调 trade-center 域。</p>
 *
 * <p><b>锚点一律取自登录态</b>（{@link #currentStoreId()}，店主账号 id == store_id），
 * <b>无条件</b>写进域入参 DTO；页面入参里<b>不含</b> {@code storeId} 字段
 * （{@code StoreOrderPageQueryDTO} / {@code StoreOrderShipDTO}）——页面能传来的锚点
 * 等于把数据权限交给页面（cross-cutting 第 22 条）。</p>
 *
 * <p><b>不加重合本层的店铺审核门禁</b>：本模块的门禁（R9，{@code status == 2}）是方法级、
 * 只覆盖 {@code /goods/**}；订单是<b>既有事实</b>、全状态可见（R4，含「待支付」——商户需要看到
 * 谁下了单没付钱），刻意不套门禁。</p>
 *
 * <p><b>状态文案不重写</b>：{@link TradeOrderVO} 自带 {@code statusStoreAdminLabel}（商户 / 管理端），
 * 域已下发，本层只用不造。出参直接是域契约类型、不另造一层 VO
 * （与 mall-bff 那份本地 {@code MallOrderVO} 不同：它要挡掉 {@code customerId} 等管理端字段，
 * 而商户侧本就需要知道单是谁下的）。</p>
 *
 * <p>下游业务异常（400 参数/业务、403、404 不存在或不属本店）沿 cause 链剥出后原样透传，
 * 其余（熔断/连接/序列化等）降级为友好提示，避免拖垮调用方。该逻辑已抽到 common 的
 * {@link BffFeignCall}，本类只传降级文案。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreOrderBffService {

    /** trade-center 熔断 / 连接异常降级提示 */
    private static final String ORDER_DEGRADE_MSG = "订单服务暂不可用，请稍后重试";

    private final TradeCenterClient tradeCenterClient;

    // ---- 订单（数据在 trade-center 域，作用域由本层从登录态无条件写入）----

    /**
     * 本店订单分页（按主键倒序 = 下单倒序），<b>全状态可见</b>。
     * <p>页面 DTO 与域 DTO 是两份类型：本层组装域入参，把 {@code storeId} 无条件写成登录态
     * （店主侧只有「本店订单」一个视角，页面无权选择看哪家店）。</p>
     *
     * @param dto 页面筛选与分页条件（订单号精确 / 状态）
     * @return 分页结果（每笔带齐明细；分页类型复用本模块 {@code /goods} 分页那份 {@link PageResult}）
     */
    public PageResult<TradeOrderVO> page(StoreOrderPageQueryDTO dto) {
        TradeOrderPageQueryDTO query = new TradeOrderPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setOrderNo(dto.getOrderNo());
        query.setStatus(dto.getStatus());
        query.setStoreId(currentStoreId());

        TradeOrderPageVO raw = callTrade(() -> tradeCenterClient.pageOrders(query));

        PageResult<TradeOrderVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(raw.getRecords());
        return result;
    }

    /**
     * 本店订单详情。
     * <p>收窄作用域后不属本店的单在域侧回 404（不区分「不存在」与「不是本店的单」），
     * 经 {@link BffFeignCall} 原样透传给页面。出参是域契约类型，本层原样下发。</p>
     *
     * @param orderNo 业务可读单号（路径变量，不是自增 id）
     * @return 订单（状态 + 地址快照 + 明细齐全）
     */
    public TradeOrderVO detail(String orderNo) {
        return callTrade(() -> tradeCenterClient.getOrder(orderNo, storeScope()));
    }

    /**
     * 发货（记录快递单号）。
     * <p>⚠ 域侧 {@code storeId} 是 {@code @NotNull}（写操作没有「合法全量视角」），由本层从登录态填；
     * 重复发货 / 跳级 → 400，本层原样透传。</p>
     *
     * @param orderNo 业务可读单号（路径变量）
     * @param dto     快递单号（页面入参，不含锚点）
     */
    public void ship(String orderNo, StoreOrderShipDTO dto) {
        TradeOrderShipDTO payload = new TradeOrderShipDTO();
        payload.setStoreId(currentStoreId());
        payload.setTrackingNo(dto.getTrackingNo());
        callTrade(() -> {
            tradeCenterClient.shipOrder(orderNo, payload);
            return null;
        });
    }

    // ---- 编排辅助 ----

    /**
     * 订单读侧的可选作用域（{@code storeId} 由本层从登录态填；域侧「传了就按它筛」）
     *
     * @return 只带店铺锚点的查询参数
     */
    private TradeOrderQueryDTO storeScope() {
        TradeOrderQueryDTO scope = new TradeOrderQueryDTO();
        scope.setStoreId(currentStoreId());
        return scope;
    }

    /**
     * 取当前登录店主账号 id（== store_id），登录态缺失时拒绝
     */
    private Long currentStoreId() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException("登录已失效，请重新登录");
        }
        return loginUser.getId();
    }

    /**
     * 调 trade-center 的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callTrade(Supplier<T> action) {
        return BffFeignCall.call("trade-center", ORDER_DEGRADE_MSG, action);
    }
}
