package com.panoramic.admin.bff;

import com.panoramic.admin.dto.OrderPageQueryDTO;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * admin 端 BFF · 订单管理编排（**只读**：分页 / 详情）。
 * <p>只做页面编排，不持有/复制 trade-center 的任何实体与表：订单数据经
 * {@link TradeCenterClient} 调 trade-center 域。平台对订单<b>只读</b>——没有任何写动作
 * （改状态 / 改单 / 删单都不做）；状态流转入口只在两端：C 端 {@code pay} / {@code receive}、
 * 商户端 {@code ship}。</p>
 *
 * <p>⚠ <b>平台侧没有数据权限锚点</b>：管理端是全量视角，故 {@link OrderPageQueryDTO} 里的
 * {@code storeId} / {@code customerId} 是<b>页面筛选条件</b>（在全量里过滤某一店 / 某一顾客），
 * <b>不是</b>作用域——本层原样搬进域入参，<b>不</b>从登录态覆盖（管理员的登录 id 既不是店铺 id
 * 也不是顾客 id）。这与顾客端 / 商户端正好相反（那边作用域只能取自登录态、页面无权选）。</p>
 *
 * <p>详情则<b>两个锚点都不传</b>（域侧省略即「不限定」= 全量视角）——管理端要看任意一笔单。</p>
 *
 * <p><b>状态文案不重写</b>：{@link TradeOrderVO} 自带 {@code statusStoreAdminLabel}（商户 / 管理端），
 * 域已下发，本层只用不造。出参直接是域契约类型、不另造一层 VO。</p>
 *
 * <p>下游业务异常（400 参数/业务，如状态取值非法；404 订单不存在）沿 cause 链剥出后原样透传，
 * 其余（熔断/连接/序列化等）降级为友好提示，避免拖垮调用方。该逻辑已抽到 common 的
 * {@link BffFeignCall}，本类只传降级文案。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderBffService {

    /** trade-center 熔断 / 连接异常降级提示 */
    private static final String ORDER_DEGRADE_MSG = "订单服务暂不可用，请稍后重试";

    private final TradeCenterClient tradeCenterClient;

    /**
     * 订单分页（跨店全量）：页面筛选条件 1:1 搬进域入参（域侧不传锚点 = 不限定，即全量），
     * 按主键倒序 = 下单倒序。
     *
     * @param dto 页面查询参数（订单号精确 / 状态 / 店铺筛选 / 顾客筛选）
     * @return 分页结果（每笔带齐明细；分页类型复用本模块 {@code /goods} 分页那份 {@link PageResult}）
     */
    public PageResult<TradeOrderVO> page(OrderPageQueryDTO dto) {
        TradeOrderPageQueryDTO query = new TradeOrderPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setOrderNo(dto.getOrderNo());
        query.setStatus(dto.getStatus());
        // 页面筛选：平台侧没有作用域锚点，原样透传（不从登录态覆盖）
        query.setStoreId(dto.getStoreId());
        query.setCustomerId(dto.getCustomerId());

        TradeOrderPageVO raw = callTrade(() -> tradeCenterClient.pageOrders(query));

        PageResult<TradeOrderVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(raw.getRecords());
        return result;
    }

    /**
     * 订单详情（任意一笔）。
     * <p>⚠ 两个锚点<b>都不传</b>：域侧「不传就是不限定」，这正是管理端需要的全量视角
     * （传了反而会把平台收窄成某一店 / 某一顾客）。域侧查不到回 404，原样透传。</p>
     *
     * @param orderNo 业务可读单号（路径变量，不是自增 id）
     * @return 订单（状态 + 地址快照 + 明细齐全）
     */
    public TradeOrderVO detail(String orderNo) {
        return callTrade(() -> tradeCenterClient.getOrder(orderNo, new TradeOrderQueryDTO()));
    }

    // ---- 编排辅助 ----

    /**
     * 调 trade-center 的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callTrade(Supplier<RespData<T>> action) {
        return BffFeignCall.call("trade-center", ORDER_DEGRADE_MSG, action);
    }
}
