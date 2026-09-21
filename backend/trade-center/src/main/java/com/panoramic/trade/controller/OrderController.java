package com.panoramic.trade.controller;

import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderPlatformPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderShipDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.trade.order.application.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单内部领域接口（trade-center 域下沉纯域）· **分顾客 / 商户 / 平台三侧**。
 *
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/trade/order/...}）调用，不向页面暴露公网路由：
 * 顾客侧 ← mall-bff（{@code OrderBffService}）、商户侧 ← store-bff（{@code StoreOrderBffService}）、
 * 平台侧 ← admin（{@code AdminOrderBffService}）。方法直接返回业务原类型（不包 {@code RespData}），
 * 错误经 {@code TradeDomainExceptionHandler} 以真实 HTTP 状态码传播。</p>
 *
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：路径上的 {@code customerId} / {@code storeId}
 * 就是数据权限锚点，它是否等于「本人 / 本店」由端 BFF 从登录态取，域侧不校验（防线在 BFF）。
 * 平台侧<b>没有锚点段</b>（管理端本就是全量视角），它要筛的店铺 / 顾客走请求体字段。</p>
 *
 * <p>⚠ <b>「分流」在调用方，不在域内</b>：域不读 {@code X-User-Type} 判身份，三侧只是三组方法
 * ——同一笔订单从顾客侧与商户侧都能读到（各自锚点各自收窄），谁该走哪条路由由端 BFF 决定。</p>
 */
@RestController
@RequestMapping("/internal/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    // ── 顾客侧 ──────────────────────────────────────────────────────────────────

    /**
     * 下单：一次提交按店铺拆成多笔，返回**整批**（顺序 = {@code storeId} 升序）
     */
    @PostMapping("/{customerId}")
    public List<TradeOrderVO> createOrder(@PathVariable("customerId") Long customerId,
                                          @Validated @RequestBody TradeOrderCreateDTO dto) {
        return orderApplicationService.create(customerId, dto);
    }

    /**
     * 我的订单分页（只含该顾客的订单）
     */
    @PostMapping("/customer/{customerId}/page")
    public TradeOrderPageVO pageCustomerOrders(@PathVariable("customerId") Long customerId,
                                               @Validated @RequestBody TradeOrderPageQueryDTO dto) {
        return orderApplicationService.pageCustomerOrders(customerId, dto);
    }

    /**
     * 我的订单详情（不存在 / 不属本人 → 404，不区分两种情形）
     */
    @GetMapping("/customer/{customerId}/{orderNo}")
    public TradeOrderVO getCustomerOrder(@PathVariable("customerId") Long customerId,
                                         @PathVariable("orderNo") String orderNo) {
        return orderApplicationService.getCustomerOrder(customerId, orderNo);
    }

    /**
     * 支付（金额必须等于订单总额；重复支付由状态机拒 → 400）
     */
    @PostMapping("/customer/{customerId}/{orderNo}/pay")
    public void payOrder(@PathVariable("customerId") Long customerId,
                         @PathVariable("orderNo") String orderNo,
                         @Validated @RequestBody TradeOrderPayDTO dto) {
        orderApplicationService.payOrder(customerId, orderNo, dto);
    }

    /**
     * 确认收货（终态）
     */
    @PostMapping("/customer/{customerId}/{orderNo}/receive")
    public void receiveOrder(@PathVariable("customerId") Long customerId,
                             @PathVariable("orderNo") String orderNo) {
        orderApplicationService.receiveOrder(customerId, orderNo);
    }

    // ── 商户侧 ──────────────────────────────────────────────────────────────────

    /**
     * 本店订单分页（**含待支付**：店主的待办起点不是「已支付」）
     */
    @PostMapping("/store/{storeId}/page")
    public TradeOrderPageVO pageStoreOrders(@PathVariable("storeId") Long storeId,
                                            @Validated @RequestBody TradeOrderPageQueryDTO dto) {
        return orderApplicationService.pageStoreOrders(storeId, dto);
    }

    /**
     * 本店订单详情（不存在 / 不属本店 → 404，不区分两种情形）
     */
    @GetMapping("/store/{storeId}/{orderNo}")
    public TradeOrderVO getStoreOrder(@PathVariable("storeId") Long storeId,
                                      @PathVariable("orderNo") String orderNo) {
        return orderApplicationService.getStoreOrder(storeId, orderNo);
    }

    /**
     * 发货（记录快递单号；重复发货 / 跳级 → 400）
     */
    @PostMapping("/store/{storeId}/{orderNo}/ship")
    public void shipOrder(@PathVariable("storeId") Long storeId,
                          @PathVariable("orderNo") String orderNo,
                          @Validated @RequestBody TradeOrderShipDTO dto) {
        orderApplicationService.shipOrder(storeId, orderNo, dto);
    }

    // ── 平台侧（无锚点） ────────────────────────────────────────────────────────

    /**
     * 平台侧订单分页（全量视角；按店铺 / 顾客筛是请求体里的可选字段）
     */
    @PostMapping("/page")
    public TradeOrderPageVO pagePlatformOrders(@Validated @RequestBody TradeOrderPlatformPageQueryDTO dto) {
        return orderApplicationService.pagePlatformOrders(dto);
    }

    /**
     * 平台侧订单详情（全量视角；不存在 → 404）
     */
    @GetMapping("/{orderNo}")
    public TradeOrderVO getPlatformOrder(@PathVariable("orderNo") String orderNo) {
        return orderApplicationService.getPlatformOrder(orderNo);
    }
}
