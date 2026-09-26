package com.panoramic.mallbff.service;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.feign.DomainResp;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeOrderAddressDTO;
import com.panoramic.contract.trade.dto.TradeOrderAddressUpdateDTO;
import com.panoramic.contract.trade.dto.TradeOrderCancelDTO;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderReceiveDTO;
import com.panoramic.contract.trade.dto.TradeOrderRefundDTO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.mallbff.dto.MallOrderAddressUpdateDTO;
import com.panoramic.mallbff.dto.MallOrderCreateDTO;
import com.panoramic.mallbff.dto.MallOrderPageQueryDTO;
import com.panoramic.mallbff.dto.MallOrderPayDTO;
import com.panoramic.mallbff.vo.MallOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * C 端订单编排（下单 / 列表 / 详情 / 支付 / 确认收货 / 改收货地址 / 取消订单 / 仅退款）。
 *
 * <p><b>三层归属</b>：订单本体（单据、状态、明细快照、金额）属 <b>trade-center</b>，
 * 收货地址属 <b>customer-center</b>，本层只做「取地址 → 组下单参数 → 调域 → 成功后清车」的页面编排，
 * 不复制任何域的表、也不重写状态文案（文案由域下发，见 {@link MallOrderVO}）。</p>
 *
 * <p>⚠ <b>唯一一处例外：订单详情要补「已评价」标记</b>——评价数据在 store 域，而「这单哪个商品能评价」
 * 只能由订单明细 + 已评价集合比对得出。本层<b>不</b>直接调 store（评价能力的落点在
 * {@link EvaluationBffService}），只把结果落到明细行的 {@code evaluated} 上；那条读<b>静默降级</b>，
 * 拿不到就不下发标记，详情照常（见 {@link #detail}）。</p>
 *
 * <p><b>锚点一律取自登录态</b>（调用方传 {@code UserContext.getUserId()}，本类不碰登录态）：
 * 八个方法都把它填进域入参 DTO（{@code page} / {@code detail} 是可选作用域，本层<b>无条件</b>写成本人，
 * 因为 C 端只有「我的订单」一个视角；{@code create} / {@code pay} / {@code receive} / {@code updateAddress}
 * / {@code cancel} / {@code refund} 上域侧是必填）。
 * 绝不从请求体 / 路径接送——域内不做任何鉴权，这个 id 就是数据权限本身。</p>
 *
 * <p>⚠ <b>读路径没有本层的「锚点非空」断言，是有意为之、且有一条必须守住的依赖</b>：
 * 域侧对<b>写</b>路径有 {@code ScopeGuard}（缺锚点 → 400），对<b>读</b>路径没有——读的作用域口径是
 * 「传了就按它筛，没传就是不限定」（cross-cutting 第 22 条），本层则无条件把本人 id 写进去。
 * 于是「锚点拿到 null」这件事的**唯一**拦点是登录闸门：`/orders*` 不在 {@code whitelist-paths} 里，
 * 且 {@code AuthTokenFilter} 重建登录态后才轮得到本层。⚠ 故若有人把 `/orders*` 加进任一侧白名单、
 * 或让身份过滤失效，后果**不是**报错而是**静默放大成全量视角**（`page` 返回全体顾客的订单、
 * `detail` 可拉任意单）。改动登录装配时必须一并回看这一条。</p>
 *
 * <p><b>下单的两步（顺序不能反）</b>：</p>
 * <ol>
 *   <li>取地址（经 {@link BffFeignCall} + 地址降级文案）→ 组装成<b>快照</b>传给域：
 *       {@code trade-center} 结构上调不到 customer-center（每个域只依赖自己的 {@code <域>-interface}），
 *       故「取地址 + 归属校验」在<b>本层</b>完成。归属校验不是本层另写的判断——域侧取单条地址时
 *       同时按 {@code id + customerId} 过滤，取不到回 404「地址不存在」（不区分「不存在」与
 *       「不属于本人」），经 {@code BffFeignCall} <b>原样透传</b>（404 不降级），
 *       故「拿别人的 addressId 下单」自然被挡住、且不透出存在性。</li>
 *   <li>下单<b>成功之后</b>、且<b>确实是购物车结算</b>（{@code source=CART}）时才清车。⚠ 反过来的话车空了什么也没买到；
 *       而 {@code DIRECT} 直购即便带了 {@code cartItemIds} 也不清——那些行从未被下单。</li>
 * </ol>
 *
 * <p>⚠ <b>清车刻意不走 {@link BffFeignCall}</b>：它<b>没有页面出口</b>——订单已建是不可逆的主结果，
 * 清车只是可重放的补偿（顾客手动删、或再提交一次都行；指纹窗口内重复提交同一批商品会<b>复用原单</b>，
 * 不会重复下单——原单已结束（已收货 / 已取消 / 已退款）时除外，那种单不参与复用，会真下出新的一笔）。
 * 套一层降级文案等于凭空给页面一个「清车失败」的假出口，而那时页面什么都做不了。
 * 故这里直接 {@code try/catch} + {@code log.warn}，<b>不让下单整体失败</b>。</p>
 *
 * <p><b>写路径直透</b>：支付 / 收货 / 改地址 / 取消 / 仅退款是「用户点了就生效」的原子操作，
 * 本层不二次判定、不补偿，一律经 {@link BffFeignCall} 调 trade-center。下游业务 4xx
 * （金额不符 / 非法状态迁移 / 该单已过期 400、订单不存在或不属本人 404）<b>原样透传</b>给页面，
 * 其余（熔断 / 连接等）才降级为 {@link #ORDER_DOWN_MSG}。</p>
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
    /** 订单详情的「已评价」标记取自评价编排（store 域的评价能力在本模块只有那一处落点） */
    private final EvaluationBffService evaluationBffService;

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
        // ⚠ 只有**购物车结算**才清车：`source=DIRECT`（详情页直购）哪怕带着 `cartItemIds` 也不清——
        // 那些行从未被下单，删掉就是静默丢顾客数据，且**不可逆**（域内是物理删除）。
        // 依赖客户端自觉是不够的：`MallOrderCreateDTO.cartItemIds` 上不可能挂「仅 CART 非空」这类跨字段校验，
        // 「仅 CART 结算带」这句话此前只写在注释里、没有任何强制点（2026-09-22 T6 复评抓出的自伤面）。
        // ⚠ trim 后比对是为了**镜像域侧的解析口径**（域内 `OrderSource.valueOf(source.trim())`）：不 trim 的话
        //    `" CART "` 在域侧照收、单照建，本层却因精确比较为 false 而不清车——两处口径不齐。
        //    不加 `toUpperCase`：那会把域侧判 400 的值也放进来（`cart` 在域侧是 400，本层不该当成清车信号）。
        // ⚠ 顺序也不能反：订单已建是主结果，清车是它的补偿；「下单失败还清车」= 车空了什么也没买到
        String source = dto.getSource();
        if (source != null && "CART".equals(source.trim())) {
            removeCartItemsQuietly(customerId, dto.getCartItemIds());
        }
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
     * <p>⚠ <b>明细细多一项「已评价」标记</b>（{@code Item.evaluated}，按 SPU）：取法是拿本单号<b>一次</b>
     * 调 store 域取已评价的 spuId 集合（不逐行调），再按 {@code spuId} 落到各行上。
     * ⚠ 它是<b>静默降级</b>的增强读：store 域不可用时集合为 {@code null} → <b>不下发该标记</b>
     * （行上留 {@code null}），详情照常返回。理由同「分类树拿不到不拖垮商品列表」——
     * 「这笔单能不能评价」的展示不该拖垮订单详情本身（见 {@link EvaluationBffService#evaluatedSpuIdsQuietly}）。</p>
     *
     * <p>⚠ <b>只有详情出口补这个标记</b>（下单 / 列表不补）：列表一页 N 单就是 N 次跨服务调用，
     * 而评价入口长在详情页上。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     * @return 订单（状态 + 地址快照 + 明细齐全）
     */
    public MallOrderVO detail(Long customerId, String orderNo) {
        MallOrderVO vo = toVO(BffFeignCall.call("trade-center", ORDER_DOWN_MSG,
                () -> tradeCenterClient.getOrder(orderNo, customerScope(customerId))));
        Set<Long> evaluatedSpuIds = evaluationBffService.evaluatedSpuIdsQuietly(customerId, orderNo);
        // null = 拿不到已评价态（store 域不可用）→ 全部留空，不写成 false（「不知道」≠「未评价」）
        if (evaluatedSpuIds != null && vo.getItems() != null) {
            vo.getItems().forEach(item -> item.setEvaluated(evaluatedSpuIds.contains(item.getSpuId())));
        }
        return vo;
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
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> tradeCenterClient.payOrder(orderNo, payload));
    }

    /**
     * 修改订单收货地址（**仅待支付可改**，闸门在域内）
     *
     * <p>⚠ <b>与下单同一个取地址路径</b>（{@link #addressSnapshot}）：页面只传 {@code addressId}，
     * 地址内容由本层取回并校验归属（域侧按 {@code id + customerId} 过滤，不属本人回 404「地址不存在」），
     * 再组快照传给 trade-center——域结构上调不到 customer-center，页面也无权自造地址内容。</p>
     *
     * <p>⚠ 它**不动顾客地址簿**，故与地址簿的四个写路径不同：这里<b>不失效地址状态缓存</b>
     * （那缓存说的是「地址簿有没有地址 / 默认是哪条」，与某一笔订单寄到哪儿无关）。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     * @param dto        新的地址 id
     */
    public void updateAddress(Long customerId, String orderNo, MallOrderAddressUpdateDTO dto) {
        TradeOrderAddressUpdateDTO payload = new TradeOrderAddressUpdateDTO();
        payload.setCustomerId(customerId);
        payload.setAddress(addressSnapshot(customerId, dto.getAddressId()));
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> tradeCenterClient.updateOrderAddress(orderNo, payload));
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
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> tradeCenterClient.receiveOrder(orderNo, payload));
    }

    /**
     * 取消订单（**仅待支付可取消**，闸门在域内：其余状态 400 原样透传）
     *
     * <p>⚠ 域侧取消**一律回补库存**（下单即扣的库存在这里还回去），故这一条是跨服务写；
     * 本层不判「能不能取消」——状态是域的事实，本层只看得到可能已经过期的快照。</p>
     *
     * <p>⚠ 「超时未支付被自动关掉」走的是**域内定时任务**，不是本接口：它没有页面出口，
     * 页面只需在拿到的状态已是「已取消」时如实展示。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     */
    public void cancel(Long customerId, String orderNo) {
        TradeOrderCancelDTO payload = new TradeOrderCancelDTO();
        payload.setCustomerId(customerId);
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> tradeCenterClient.cancelOrder(orderNo, payload));
    }

    /**
     * 仅退款（**仅「已支付、未发货」可退**，闸门在域内：其余状态 400 原样透传）
     *
     * <p>⚠ 一步生效、无需商户同意，全额退、不传金额——退款金额恒等于订单总额（聚合里冻结的那个值），
     * 让页面传金额等于给「退多少」开出第二个说了算的地方。</p>
     *
     * <p>⚠ 与{@link #cancel} 是**两个动作**、不是同一个：取消说的是「没付过钱的单不买了」，
     * 仅退款说的是「付过的钱退回去」——域侧是两个状态、两条迁移边，故本层也是两个出口。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param orderNo    业务可读单号
     */
    public void refund(Long customerId, String orderNo) {
        TradeOrderRefundDTO payload = new TradeOrderRefundDTO();
        payload.setCustomerId(customerId);
        BffFeignCall.call("trade-center", ORDER_DOWN_MSG, () -> tradeCenterClient.refundOrder(orderNo, payload));
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
     * @param cartItemIds 待清理的购物车行 id；为空时什么都不做
     */
    private void removeCartItemsQuietly(Long customerId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return;
        }
        TradeCartItemIdsDTO payload = new TradeCartItemIdsDTO();
        payload.setCustomerId(customerId);
        payload.setIds(cartItemIds);
        try {
            // ⚠ 必须解包：域侧业务失败是 code≠200（不抛异常），丢掉返回值就等于把失败静默吞掉、
            //   连下面那条 warn 都不会打（cross-cutting 第 2 条）。unwrap 抛出的异常走同一个 catch。
            DomainResp.unwrap(tradeCenterClient.removeCartItems(payload));
        } catch (Exception e) {
            // 订单已建、不可逆；清车可重放（顾客手动删 / 再提交一次会命中幂等复用原单），故只记日志。
            // ⚠ catch 面刻意宽到 Exception（与本端静默降级先例一致：CatalogBffService / CustomerProfileBffService 同款）：
            //   本方法在**订单已建之后**执行，漏网的异常会逃出 create 让页面拿到 500「订单暂不可用」，
            //   而订单与库存都已生效——顾客重试即二次下单 / 二次扣减。宁可不记全，也不能漏。
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
        // 支付截止时刻原样透传：倒计时（页面）与「是否过期」（域侧支付校验）必须同一份时刻，
        // 本层再算一次就是第二个说了算的地方。null = 无超时（老单），照传，由页面决定不倒计时
        vo.setExpireTime(source.getExpireTime());
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
