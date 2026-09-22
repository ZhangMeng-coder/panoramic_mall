package com.panoramic.mallbff.controller;

import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.mallbff.dto.MallOrderCreateDTO;
import com.panoramic.mallbff.dto.MallOrderPageQueryDTO;
import com.panoramic.mallbff.dto.MallOrderPayDTO;
import com.panoramic.mallbff.service.OrderBffService;
import com.panoramic.mallbff.vo.MallOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端订单接口（下单 / 列表 / 详情 / 支付 / 确认收货，共 5 条）。
 *
 * <p><b>形状</b>：本类只碰 {@link OrderBffService}，<b>不注入</b>任何 Feign 客户端，
 * 页面类型 ↔ 域契约类型的映射收在该 service 内。出参一律包 {@code RespData}（含 {@code void} 的写路径）。</p>
 *
 * <p>⚠ <b>顾客 id 只能取自 {@code UserContext}</b>（登录态），绝不从请求体 / 路径接收：
 * 域内不做任何鉴权（{@code customerId} 就是数据权限本身），BFF 是唯一授权点；
 * 域侧每条读写都按该 id 过滤，故拿不到别人的单。</p>
 *
 * <p>⚠ <b>订单标识走路径变量的是 {@code orderNo}</b>（业务可读单号），不是自增 id——
 * 理由与形状见 {@code docs/contracts/mall-bff.md}。</p>
 *
 * <p>⚠ <b>C 端不接 RBAC</b>：本类没有、也不应有任何 {@code @PreAuthorize}——顾客对自己的订单全权限。
 * 本类全部路径<b>均需登录态</b>（登记在 {@code application.yml} 的「需登录态端点」注），
 * <b>不得</b>加进任何免鉴权白名单。</p>
 *
 * <p><b>错误形状</b>：下游业务 4xx 原样透传——「订单不存在 / 不属本人」404、「商品不可购买 / 库存不足 /
 * 数量越界 / 地址非法 / 支付金额与订单总额不一致 / 非法状态迁移」400、「地址不存在」404 如实回页面；
 * 只有下游故障才降级为 500「订单暂不可用，请稍后重试」（取地址那一步降级文案是「地址服务暂不可用」）。</p>
 *
 * <p>⚠ <b>下单是两步</b>：先建单、成功后才清车（{@code cartItemIds} 非空时）。
 * 清车失败<b>不会</b>让本接口失败——详情与理由见 {@link OrderBffService}。</p>
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderBffService orderBffService;

    /**
     * 下单（⚠ 一次提交按店铺<b>拆成多笔</b>，故出参是列表，顺序 = storeId 升序）
     * <p>{@code source=DIRECT} 直购时 {@code cartItemIds} 为空；{@code source=CART} 结算时带上待清理的
     * 购物车行 id——它们<b>不会</b>被传给域，只用于建单成功后本层清车。</p>
     */
    @PostMapping
    public RespData<List<MallOrderVO>> create(@Valid @RequestBody MallOrderCreateDTO dto) {
        return RespData.success(orderBffService.create(UserContext.getUserId(), dto));
    }

    /**
     * 我的订单分页（订单号精确 + 状态筛选）
     * <p>⚠ 裸 {@code @Valid}：分页字段的约束落在 {@code BasePageVO} 的默认组
     * （理由见 {@code CatalogController#goods}）。</p>
     */
    @PostMapping("/page")
    public RespData<PageResult<MallOrderVO>> page(@Valid @RequestBody MallOrderPageQueryDTO dto) {
        return RespData.success(orderBffService.page(UserContext.getUserId(), dto));
    }

    /**
     * 订单详情（不属本人的单 → 404 原样透传，不区分「不存在」与「不是你的单」）
     */
    @GetMapping("/{orderNo}")
    public RespData<MallOrderVO> detail(@PathVariable("orderNo") String orderNo) {
        return RespData.success(orderBffService.detail(UserContext.getUserId(), orderNo));
    }

    /**
     * 支付（假支付：{@code amount} 必须等于订单总额，否则域侧 400 原样透传）
     */
    @PostMapping("/{orderNo}/pay")
    public RespData<Void> pay(@PathVariable("orderNo") String orderNo,
                              @Valid @RequestBody MallOrderPayDTO dto) {
        orderBffService.pay(UserContext.getUserId(), orderNo, dto);
        return RespData.success();
    }

    /**
     * 确认收货（终态；重复确认由域侧状态机拒，400 原样透传）
     */
    @PostMapping("/{orderNo}/receive")
    public RespData<Void> receive(@PathVariable("orderNo") String orderNo) {
        orderBffService.receive(UserContext.getUserId(), orderNo);
        return RespData.success();
    }
}
