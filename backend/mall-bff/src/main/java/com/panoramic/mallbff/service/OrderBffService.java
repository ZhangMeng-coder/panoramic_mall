package com.panoramic.mallbff.service;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeOrderAddressDTO;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderReceiveDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.mallbff.dto.MallOrderCreateDTO;
import com.panoramic.mallbff.dto.MallOrderPageQueryDTO;
import com.panoramic.mallbff.dto.MallOrderPayDTO;
import com.panoramic.mallbff.vo.MallOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * C 端订单编排（下单 / 列表 / 详情 / 支付 / 确认收货）。
 *
 * <p><b>三层归属</b>：订单本体（单据、状态、明细快照、金额）属 <b>trade-center</b>，
 * 收货地址属 <b>customer-center</b>，本层只做「取地址 → 组下单参数 → 调域 → 成功后清车」的页面编排，
 * 不复制任何域的表、也不重写状态文案（文案由域下发，见 {@link MallOrderVO}）。</p>
 *
 * <p><b>锚点一律取自登录态</b>（调用方传 {@code UserContext.getUserId()}，本类不碰登录态）：
 * 五个方法都把它填进域入参 DTO（{@code page} / {@code detail} 是可选作用域，本层<b>无条件</b>写成本人，
 * 因为 C 端只有「我的订单」一个视角；{@code create} / {@code pay} / {@code receive} 上域侧是必填）。
 * 绝不从请求体 / 路径接送——域内不做任何鉴权，这个 id 就是数据权限本身。</p>
 *
 * <p><b>下单的两步（顺序不能反）</b>：</p>
 * <ol>
 *   <li>取地址（经 {@link BffFeignCall} + 地址降级文案）→ 组装成<b>快照</b>传给域：
 *       {@code trade-center} 结构上调不到 customer-center（每个域只依赖自己的 {@code <域>-interface}），
 *       故「取地址 + 归属校验」在<b>本层</b>完成。归属校验不是本层另写的判断——域侧取单条地址时
 *       同时按 {@code id + customerId} 过滤，取不到回 404「地址不存在」（不区分「不存在」与
 *       「不属于本人」），经 {@code BffFeignCall} <b>原样透传</b>（404 不降级），
 *       故「拿别人的 addressId 下单」自然被挡住、且不透出存在性。</li>
 *   <li>下单<b>成功之后</b>才清车（{@code cartItemIds} 非空时）。⚠ 反过来的话车空了什么也没买到。</li>
 * </ol>
 *
 * <p>⚠ <b>清车刻意不走 {@link BffFeignCall}</b>：它<b>没有页面出口</b>——订单已建是不可逆的主结果，
 * 清车只是可重放的补偿（顾客手动删、或再提交一次都行；指纹窗口内重复提交同一批商品会<b>复用原单</b>，
 * 不会重复下单）。套一层降级文案等于凭空给页面一个「清车失败」的假出口，而那时页面什么都做不了。
 * 故这里直接 {@code try/catch} + {@code log.warn}，<b>不让下单整体失败</b>。</p>
 *
 * <p><b>写路径直透</b>：支付 / 收货是「用户点了就生效」的原子操作，本层不二次判定、不补偿，
 * 一律经 {@link BffFeignCall} 调 trade-center。下游业务 4xx（金额不符 / 非法状态迁移 400、
 * 订单不存在或不属本人 404）<b>原样透传</b>给页面，其余（熔断 / 连接等）才降级为
 * {@link #ORDER_DOWN_MSG}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBffService {

    /** trade-center 熔断 / 连接异常降级提示 */
    private static final String ORDER_DOWN_MSG = "订单暂不可用，请稍后重试";
    /** customer-center 熔断 / 连接异常降级提示（与 {@code CustomerAddressBffService} 同一句，出口文案保持一致） */
    private static final String ADDRESS_DOWN_MSG = "地址服务暂不可用，请稍后重试";

    private final TradeCenterClient tradeCenterClient;
    private final CustomerCenterClient customerCenterClient;

    /**
     * 下单：取地址快照 → 调域（一次提交按店铺拆成多笔）→ <b>成功后</b>清车。
     *
     * @param customerId 顾客账号 id（<b>只能取自登录态</b>，见 controller）
     * @param dto        页面下单参数（地址 id + 商品行 + 可选购物车行 id）
     * @return 本次提交的整批订单（至少一笔；顺序 = storeId 升序），页面按「一笔一单」展示与支付
     */
    public List<MallOrderVO> create(Long customerId, MallOrderCreateDTO dto) {
        TradeOrderCreateDTO payload = new TradeOrderCreateDTO();
        payload.setCustomerId(customerId);
        payload.setSource(dto.getSource());
        payload.setRequestId(dto.getRequestId());
        payload.setAddress(addressSnapshot(customerId, dto.getAddressId()));
        payload.setItems(dto.getItems().stream().map(item -> {
            TradeOrderCreateDTO.Item row = new TradeOrderCreateDTO.Item();
            row.setSkuId(item.getSkuId());
            row.setQuantity(item.getQuantity());
            return row;
        }).toList());

        List<TradeOrderVO> created = BffFeignCall.call("trade-center", ORDER_DOWN_MSG,
                () -> tradeCenterClient.createOrder(payload));
        // ⚠ 顺序不能反：订单已建是主结果，清车是它的补偿；且「下单失败还清车」= 车空了什么也没买到
        removeCartItemsQuietly(customerId, dto.getCartItemIds());
        return created == null ? List.of() : created.stream().map(this::toVO).toList();
    }

    /**
     * 我的订单分页（按主键倒序 = 下单倒序）。
     * <p>⚠ 作用域<b>无条件</b>写成登录态：C 端只有「我的订单」一个视角，页面无权选择看谁的单。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param dto        页面筛选与分页条件（订单号精确 / 状态）
     * @return 分页结果（每笔带齐明细；出参分页类型复用本模块既有的 store 那份 {@link PageResult}）
     */
    public PageResult<MallOrderVO> page(Long customerId, MallOrderPageQueryDTO dto) {
        TradeOrderPageQueryDTO query = new TradeOrderPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setOrderNo(dto.getOrderNo());
        query.setStatus(dto.getStatus());
        query.setCustomerId(customerId);
        TradeOrderPageVO raw = BffFeignCall.call("trade-center", ORDER_DOWN_MSG,
                () -> tradeCenterClient.pageOrders(query));

        PageResult<MallOrderVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(this::toVO).toList());
        return result;
    }

    /**
     * 订单详情。
     * <p>⚠ 传作用域即收窄：不属本人的单在域侧回 404（不区分「不存在」与「不是你的单」），原样透传给页面。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     * @return 订单（状态 + 地址快照 + 明细齐全）
     */
    public MallOrderVO detail(Long customerId, String orderNo) {
        return toVO(BffFeignCall.call("trade-center", ORDER_DOWN_MSG,
                () -> tradeCenterClient.getOrder(orderNo, customerScope(customerId))));
    }

    /**
     * 支付（假支付）。
     * <p>⚠ 金额是否付对由<b>域内</b>比对（本层只透传）；不一致 → 400，本层原样透传（不另译成
     * 「请勿重复操作」之类的句子——同一句提示只此一份）。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     * @param dto        本次支付金额
     */
    public void pay(Long customerId, String orderNo, MallOrderPayDTO dto) {
        TradeOrderPayDTO payload = new TradeOrderPayDTO();
        payload.setCustomerId(customerId);
        payload.setAmount(dto.getAmount());
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> {
            tradeCenterClient.payOrder(orderNo, payload);
            return null;
        });
    }

    /**
     * 确认收货（终态；已收货再调用 → 400 原样透传）
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     */
    public void receive(Long customerId, String orderNo) {
        TradeOrderReceiveDTO payload = new TradeOrderReceiveDTO();
        payload.setCustomerId(customerId);
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> {
            tradeCenterClient.receiveOrder(orderNo, payload);
            return null;
        });
    }

    // ---- 内部 ----

    /**
     * 取收货地址并转成下单需要的<b>四字段快照</b>。
     * <p>经 {@link BffFeignCall}：地址是下单的<b>前置数据</b>（拿不到就不该下单），不是增强，
     * 故「地址不存在」（404）与「地址服务不可用」（降级 500）都要如实回页面。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态；同时是归属校验的作用域）
     * @param addressId  地址 id（页面传来）
     * @return 域侧地址快照（字段名 {@code detail} ← 顾客地址的 {@code detailAddress}）
     */
    private TradeOrderAddressDTO addressSnapshot(Long customerId, Long addressId) {
        CustomerAddressVO address = BffFeignCall.call("customer-center", ADDRESS_DOWN_MSG,
                () -> customerCenterClient.getAddress(customerId, addressId));
        TradeOrderAddressDTO snapshot = new TradeOrderAddressDTO();
        snapshot.setReceiverName(address.getReceiverName());
        snapshot.setReceiverPhone(address.getReceiverPhone());
        snapshot.setRegion(address.getRegion());
        snapshot.setDetail(address.getDetailAddress());
        return snapshot;
    }

    /**
     * 订单详情的可选作用域（{@code customerId} 必填语义由本层保证：C 端只有本人视角）
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @return 只带顾客锚点的查询参数
     */
    private TradeOrderQueryDTO customerScope(Long customerId) {
        TradeOrderQueryDTO scope = new TradeOrderQueryDTO();
        scope.setCustomerId(customerId);
        return scope;
    }

    /**
     * 下单成功后的清车（<b>补偿</b>，不是主结果）。
     *
     * <p>⚠ <b>刻意不走 {@link BffFeignCall}</b>（理由见类注释）：失败只 {@code log.warn}，
     * 既不套「购物车暂不可用」那类降级文案，也不让下单整体失败。复用的是既有的批量删除端点
     * （{@code POST /cart/items/remove}，域内物理删除、幂等、删 0 行不报错），<b>不是新接口</b>；
     * 域侧 SQL 按 {@code customer_id + id IN (…)} 过滤，故页面伪造的 id 删不到别人的行。</p>
     *
     * @param customerId  顾客账号 id（只能取自登录态）
     * @param cartItemIds 待清理的购物车行 id；为空（{@code DIRECT} 直购）时什么都不做
     */
    private void removeCartItemsQuietly(Long customerId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return;
        }
        TradeCartItemIdsDTO payload = new TradeCartItemIdsDTO();
        payload.setCustomerId(customerId);
        payload.setIds(cartItemIds);
        try {
            tradeCenterClient.removeCartItems(payload);
        } catch (RuntimeException e) {
            // 订单已建、不可逆；清车可重放（顾客手动删 / 再提交一次会命中幂等复用原单），故只记日志
            log.warn("下单后清车失败（订单已建，属可重放补偿）: customerId={}, cartItemIds={}", customerId, cartItemIds, e);
        }
    }

    /**
     * 域订单 → C 端订单（<b>逐字段手工映射</b>，不用 {@code BeanUtils.copyProperties}：
     * 域 VO 是所有调用方共用的超集，含 {@code customerId} 与 {@code statusStoreAdminLabel}，
     * 域返回的字段不等于可以对外暴露）。
     *
     * @param source 域订单
     * @return C 端订单
     */
    private MallOrderVO toVO(TradeOrderVO source) {
        MallOrderVO vo = new MallOrderVO();
        vo.setOrderNo(source.getOrderNo());
        vo.setStoreName(source.getStoreName());
        vo.setSource(source.getSource());
        vo.setStatus(source.getStatus());
        // 状态文案原样取域的 mallLabel：本层重写一份必与域漂移
        vo.setStatusMallLabel(source.getStatusMallLabel());
        vo.setTotalQuantity(source.getTotalQuantity());
        vo.setTotalAmount(source.getTotalAmount());
        vo.setShipNo(source.getShipNo());
        vo.setCreateTime(source.getCreateTime());
        vo.setAddress(toAddress(source.getAddress()));
        if (source.getItems() != null) {
            vo.setItems(source.getItems().stream().map(this::toItem).toList());
        }
        return vo;
    }

    /**
     * 域地址快照 → 页面地址（字段名不同源：{@code detail} → {@code detailAddress}）
     *
     * @param source 域地址快照；可为 {@code null}（理论上不会）
     * @return 页面地址
     */
    private MallOrderVO.Address toAddress(TradeOrderAddressDTO source) {
        if (source == null) {
            return null;
        }
        MallOrderVO.Address address = new MallOrderVO.Address();
        address.setReceiverName(source.getReceiverName());
        address.setReceiverPhone(source.getReceiverPhone());
        address.setRegion(source.getRegion());
        address.setDetailAddress(source.getDetail());
        return address;
    }

    /**
     * 域明细行 → 页面明细行（逐字段映射；{@code specAttrs} 直接透传，无规格时域给空表）
     *
     * @param source 域明细行
     * @return 页面明细行
     */
    private MallOrderVO.Item toItem(TradeOrderVO.Item source) {
        MallOrderVO.Item item = new MallOrderVO.Item();
        item.setSkuId(source.getSkuId());
        item.setSpuId(source.getSpuId());
        item.setGoodsName(source.getGoodsName());
        item.setMainImage(source.getMainImage());
        item.setSpecAttrs(source.getSpecAttrs());
        item.setUnitPrice(source.getUnitPrice());
        item.setQuantity(source.getQuantity());
        item.setSubtotal(source.getSubtotal());
        return item;
    }
}
