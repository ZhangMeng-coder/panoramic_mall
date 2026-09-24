package com.panoramic.mallbff.controller;

import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.mallbff.dto.MallOrderAddressUpdateDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端订单接口（下单 / 列表 / 详情 / 支付 / 确认收货 / 改收货地址 / 取消订单 / 仅退款，共 8 条）。
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
 * 数量越界 / 地址非法 / 支付金额与订单总额不一致 / 该订单已过期 / 非法状态迁移」400、「地址不存在」404
 * 如实回页面；只有下游故障才降级为 500「订单暂不可用，请稍后重试」（取地址那一步降级文案是「地址服务暂不可用」）。</p>
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
     * 修改收货地址（**仅待支付可改**，闸门在域内 → 其余状态 400 原样透传）
     *
     * <p>⚠ 入参是 {@code addressId} 而不是地址字段：地址由本层取回 + 校验归属后组快照传域
     * （见 {@link OrderBffService#updateAddress}）。出参 {@code Void}，页面改完重拉详情。</p>
     */
    @PutMapping("/{orderNo}/address")
    public RespData<Void> updateAddress(@PathVariable("orderNo") String orderNo,
                                        @Valid @RequestBody MallOrderAddressUpdateDTO dto) {
        orderBffService.updateAddress(UserContext.getUserId(), orderNo, dto);
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

    /**
     * 取消订单（**仅待支付可取消**，闸门在域内 → 其余状态 400 原样透传）
     *
     * <p>⚠ <b>没有请求体</b>：这个动作的载荷只有「哪一笔单」，而作用域 customerId 取自登录态、
     * 单号在路径里——所以本端不立 DTO。<b>不要</b>为「以后可能加取消原因」先塞一个空 DTO 进来：
     * 那是一个没有任何字段的壳，等于给一个不存在的载荷预留位置。</p>
     *
     * <p>⚠ 域侧取消会<b>回补库存</b>；出参 {@code Void}，页面拿返回值重拉详情 / 列表。</p>
     */
    @PostMapping("/{orderNo}/cancel")
    public RespData<Void> cancel(@PathVariable("orderNo") String orderNo) {
        orderBffService.cancel(UserContext.getUserId(), orderNo);
        return RespData.success();
    }

    /**
     * 仅退款（**仅「已支付、未发货」可退**，闸门在域内 → 其余状态 400 原样透传）
     *
     * <p>⚠ 与{@link #cancel} 是<b>两个动作</b>：前者是「没付过钱的单不买了」，后者是「付过的钱退回去」。
     * 域侧是两个状态、两条迁移边，故页面也是两个按钮——<b>不要</b>在后端合并成一个「取消/退款」入口
     * （合并就得在本层猜状态，而状态是域的事实）。</p>
     *
     * <p>⚠ 同样<b>没有请求体</b>（理由见{@link #cancel}）；全额退、不传金额，金额由域侧取聚合里冻结的订单总额。</p>
     */
    @PostMapping("/{orderNo}/refund")
    public RespData<Void> refund(@PathVariable("orderNo") String orderNo) {
        orderBffService.refund(UserContext.getUserId(), orderNo);
        return RespData.success();
    }
}
