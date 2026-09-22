package com.panoramic.storebff.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.storebff.bff.StoreOrderBffService;
import com.panoramic.storebff.dto.StoreOrderPageQueryDTO;
import com.panoramic.storebff.dto.StoreOrderShipDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺端 BFF · 商户侧订单接口（列表 / 详情 / 发货，共 3 条）。
 * <p>页面请求经网关 {@code /store/orders/**} → 本控制器 → 内部 Feign 调 trade-center 域。
 * 只做聚合与包装（{@code RespData}），不持有 trade-center 的任何实体与表。</p>
 *
 * <p>⚠ <b>作用域取自登录态</b>：本类<b>不接收</b> {@code storeId}（页面入参里也没有该字段），
 * 由 {@link StoreOrderBffService} 从 {@code UserContext} 取当前店主账号 id 后无条件写进域入参 DTO
 * ——域内不做任何鉴权，这个 id 就是数据权限本身（cross-cutting 第 22 条）。</p>
 *
 * <p>⚠ <b>无 {@code @PreAuthorize}</b>：店铺端不接 RBAC（登录态是唯一门槛），店主对自己店的订单
 * 全权限。⚠ 也<b>不套</b>本模块 {@code /goods/**} 的店铺审核门禁（R9）：订单是既有事实、
 * 全状态可见（R4，含「待支付」），刻意不加。</p>
 *
 * <p>⚠ <b>订单标识走路径变量的是 {@code orderNo}</b>（业务可读单号），不是自增 id。
 * 出参直接是域契约类型 {@link TradeOrderVO}（状态文案取其中的 {@code statusStoreAdminLabel}，
 * 域已下发、本层不重写）。</p>
 *
 * <p><b>错误形状</b>：下游业务 4xx 原样透传——「订单不存在或不属本店」404、「重复发货 / 跳级 /
 * 单号为空 / 状态取值非法」400 如实回页面；只有下游故障才降级为 500
 * {@code 订单服务暂不可用，请稍后重试}。</p>
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final StoreOrderBffService storeOrderBffService;

    /**
     * 本店订单分页（订单号精确 + 状态筛选；全状态可见，含「待支付」）
     */
    @GetMapping("/page")
    public RespData<PageResult<TradeOrderVO>> page(@Validated StoreOrderPageQueryDTO dto) {
        return RespData.success(storeOrderBffService.page(dto));
    }

    /**
     * 本店订单详情（不属本店的单 → 404 原样透传，不区分「不存在」与「不是本店的单」）
     */
    @GetMapping("/{orderNo}")
    public RespData<TradeOrderVO> detail(@PathVariable("orderNo") String orderNo) {
        return RespData.success(storeOrderBffService.detail(orderNo));
    }

    /**
     * 发货（记录快递单号；重复发货 / 跳级由域侧状态机拒，400 原样透传）
     */
    @PostMapping("/{orderNo}/ship")
    public RespData<Void> ship(@PathVariable("orderNo") String orderNo,
                               @Valid @RequestBody StoreOrderShipDTO dto) {
        storeOrderBffService.ship(orderNo, dto);
        return RespData.success();
    }
}
