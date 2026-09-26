package com.panoramic.trade.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.trade.dto.TradeOrderAddressUpdateDTO;
import com.panoramic.contract.trade.dto.TradeOrderCancelDTO;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderReceiveDTO;
import com.panoramic.contract.trade.dto.TradeOrderRefundDTO;
import com.panoramic.contract.trade.dto.TradeOrderShipDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.trade.order.application.OrderApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单内部领域接口（trade-center 域下沉纯域）· **按能力九条**，不按端分侧。
 *
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/trade/order/...}）调用，不向页面暴露公网路由。
 * 出参一律包 {@code RespData<T>}（cross-cutting 第 2 条）：业务结果（含业务失败，如状态机拒重复动作）
 * 走 HTTP 200 + {@code code}，异常由 common 的 {@code GlobalExceptionHandler} 兜底成 HTTP 500 ——
 * 那是熔断唯一的失败信号（第 13 条）。</p>
 *
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：数据作用域（{@code customerId} / {@code storeId}）
 * 由调用方填在**请求体 DTO 的字段**里（不进路径段，cross-cutting 第 22 条），域侧只做「传了就按它筛，
 * 没传就是不限定」。它是否等于「本人 / 本店」由端 BFF 从登录态取，域侧不校验（防线在 BFF）。
 * 读能力（分页 / 详情）的作用域字段**可选**（管理端本就全量视角），写能力
 * （下单 / 支付 / 发货 / 收货 / 改地址 / 取消 / 仅退款）
 * 的**必填**——那份必填由 DTO 上的 {@code @NotNull} 守。</p>
 *
 * <p>⚠ <b>不分端、不判身份</b>：同一笔订单从顾客侧与商户侧都能读到（各自传各自的作用域收窄），
 * 谁该传什么由端 BFF 决定；域不读 {@code X-User-Type}、不做端别分流。</p>
 */
@RestController
@RequestMapping("/internal/trade/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderApplicationService orderApplicationService;

    /**
     * 下单：一次提交按店铺拆成多笔，返回**整批**（顺序 = {@code storeId} 升序）。
     * ⚠ 作用域 {@code customerId} 必填（在 DTO 上，值由 mall-bff 从登录态填）
     */
    @PostMapping
    public RespData<List<TradeOrderVO>> createOrder(@Validated @RequestBody TradeOrderCreateDTO dto) {
        return RespData.success(orderApplicationService.create(dto));
    }

    /**
     * 订单分页：传 {@code customerId} 即「我的订单」、传 {@code storeId} 即「本店订单」
     * （**含待支付**：店主的待办起点不是「已支付」）、都不传即全量
     */
    @PostMapping("/page")
    public RespData<TradeOrderPageVO> pageOrders(@Validated @RequestBody TradeOrderPageQueryDTO dto) {
        return RespData.success(orderApplicationService.pageOrders(dto));
    }

    /**
     * 订单详情：传作用域即收窄，都不传即全量；不存在 / 不在作用域内 → {@code code=404}，不区分两种情形
     */
    @GetMapping("/{orderNo}")
    public RespData<TradeOrderVO> getOrder(@PathVariable("orderNo") String orderNo,
                                           @Validated TradeOrderQueryDTO dto) {
        return RespData.success(orderApplicationService.getOrder(orderNo, dto));
    }

    /**
     * 修改收货地址（**仅待支付**；只改这一笔的地址快照，不动顾客地址簿）。
     * ⚠ 它是「同状态内字段替换」而不是状态流转，故用 {@code PUT}（替换子资源）——与下面三个
     * 命令语义的 {@code POST} 刻意不同
     */
    @PutMapping("/{orderNo}/address")
    public RespData<Void> updateOrderAddress(@PathVariable("orderNo") String orderNo,
                                             @Validated @RequestBody TradeOrderAddressUpdateDTO dto) {
        orderApplicationService.updateAddress(orderNo, dto);
        return RespData.success();
    }

    /**
     * 支付（金额必须等于订单总额；重复支付由状态机拒 → {@code code=400}）
     */
    @PostMapping("/{orderNo}/pay")
    public RespData<Void> payOrder(@PathVariable("orderNo") String orderNo,
                                  @Validated @RequestBody TradeOrderPayDTO dto) {
        orderApplicationService.payOrder(orderNo, dto);
        return RespData.success();
    }

    /**
     * 发货（记录快递单号；重复发货 / 这笔单当前状态不允许发货 → {@code code=400}）
     */
    @PostMapping("/{orderNo}/ship")
    public RespData<Void> shipOrder(@PathVariable("orderNo") String orderNo,
                                   @Validated @RequestBody TradeOrderShipDTO dto) {
        orderApplicationService.shipOrder(orderNo, dto);
        return RespData.success();
    }

    /**
     * 确认收货（终态）
     */
    @PostMapping("/{orderNo}/receive")
    public RespData<Void> receiveOrder(@PathVariable("orderNo") String orderNo,
                                      @Validated @RequestBody TradeOrderReceiveDTO dto) {
        orderApplicationService.receiveOrder(orderNo, dto);
        return RespData.success();
    }

    /**
     * 取消订单（**仅待支付可取消**；回补库存）。
     * ⚠ 它与超时关单任务共用同一份域侧口径，差异只在触发方
     */
    @PostMapping("/{orderNo}/cancel")
    public RespData<Void> cancelOrder(@PathVariable("orderNo") String orderNo,
                                     @Validated @RequestBody TradeOrderCancelDTO dto) {
        orderApplicationService.cancelOrder(orderNo, dto);
        return RespData.success();
    }

    /**
     * 仅退款（**仅「已支付、未发货」可退**，全额退、一步生效；回补库存）
     */
    @PostMapping("/{orderNo}/refund")
    public RespData<Void> refundOrder(@PathVariable("orderNo") String orderNo,
                                     @Validated @RequestBody TradeOrderRefundDTO dto) {
        orderApplicationService.refundOrder(orderNo, dto);
        return RespData.success();
    }
}
