package com.panoramic.admin.controller.order;

import com.panoramic.admin.bff.AdminOrderBffService;
import com.panoramic.admin.dto.OrderPageQueryDTO;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * admin 端 BFF · 订单管理编排接口（**只读**，2 条：分页 / 详情）。
 * <p>页面请求经网关 {@code /admin/orders/**} → 本控制器 → 内部 Feign 调 trade-center 域
 * （平台侧是全量视角、没有数据权限锚点）。只做聚合与包装（{@code RespData}），
 * 不持有 trade-center 的任何实体与表。</p>
 *
 * <p>⚠ <b>平台对订单只读</b>——无任何写动作（改状态 / 改单 / 删单都不做）；状态流转入口只在
 * 两端：C 端 {@code pay} / {@code receive}、商户端 {@code ship}。</p>
 *
 * <p>⚠ <b>筛选 ≠ 作用域</b>：{@link OrderPageQueryDTO} 里的 {@code storeId} / {@code customerId}
 * 是页面在全量里过滤的<b>筛选条件</b>，原样传给域；本层<b>不</b>从登录态覆盖（管理员的登录 id
 * 既不是店铺 id 也不是顾客 id）。</p>
 *
 * <p>⚠ <b>授权只在此层</b>：两个端点都挂 {@code @PreAuthorize("hasAuthority('trade:order:list')")}
 * （权限串三方一致的登记见 {@code docs/contracts/admin.md}）。</p>
 *
 * <p>⚠ 订单标识走路径变量的是 {@code orderNo}（业务可读单号），不是自增 id。出参直接是域契约类型
 * {@link TradeOrderVO}（状态文案取其中的 {@code statusStoreAdminLabel}，域已下发、本层不重写）。</p>
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final AdminOrderBffService adminOrderBffService;

    /**
     * 订单分页（跨店全量；订单号精确 / 状态 / 店铺 / 顾客筛选）
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('trade:order:list')")
    public RespData<PageResult<TradeOrderVO>> page(@Validated OrderPageQueryDTO dto) {
        return RespData.success(adminOrderBffService.page(dto));
    }

    /**
     * 订单详情（任意一笔；不存在 → 404 原样透传）
     */
    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAuthority('trade:order:list')")
    public RespData<TradeOrderVO> detail(@PathVariable("orderNo") String orderNo) {
        return RespData.success(adminOrderBffService.detail(orderNo));
    }
}
