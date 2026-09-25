package com.panoramic.mallbff.service;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.SpecAttr;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationOrderQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationStatQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationSubmitDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationSkuVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationStatVO;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeOrderQueryDTO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import com.panoramic.mallbff.dto.MallEvaluationPageQueryDTO;
import com.panoramic.mallbff.dto.MallEvaluationSubmitDTO;
import com.panoramic.mallbff.vo.MallEvaluationItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * C 端评价编排（提交 / 商品评价分页 / 星级分布 + 订单详情的「已评价」标记）。
 *
 * <p><b>三层归属</b>：评价数据（含评分冗余列）全在 <b>store 域</b>（{@code store_goods_evaluation}
 * + {@code store_goods_spu.score} / {@code store_shop.score}）；订单状态属 <b>trade-center</b>；
 * 顾客昵称 / 头像属 <b>customer-center</b>。本层<b>只编排、不落表</b>。</p>
 *
 * <p><b>提交评价的三件编排（都在本层，域侧都做不了）</b>：</p>
 * <ol>
 *   <li><b>订单门禁</b>（门禁 = 业务前置条件校验，<b>不是鉴权</b>）：经 trade-center 取该单（带登录态
 *       {@code customerId} 作用域）→ 校验状态为「已收货」({@code RECEIVED})。⚠ <b>门禁为什么不在域内</b>：
 *       域不持订单，判状态就要新增 {@code store → trade} 的域间边（违反 cross-cutting 第 24 条：
 *       全仓唯一的跨域边是 {@code trade-center → store}）。<b>与「店铺审核门禁在端 BFF」同一先例。</b></li>
 *   <li><b>按 {@code spuId} 归组 SKU 行</b>（一单可含同一 SPU 的多个 SKU）→ 组 {@code skuSnapshot} 快照：
 *       规格 / 单价 / 数量是「成交那一刻的事实」，只能来自订单明细，页面无权自造。</li>
 *   <li>调 store 域写评价（域侧按 {@code spuId} 反查 {@code store_id}）。⚠ 单域写入、<b>本地事务</b>，
 *       不涉及 {@code @GlobalTransactional}。</li>
 * </ol>
 *
 * <p>⚠ <b>门禁失败一律同一句 400</b>（「订单不存在或尚未完成，无法评价」）：订单取不到（不存在 /
 * 不属本人）与状态不是「已收货」在页面上<b>同形</b>，不让人拿这句话术当「这笔单是否存在」的探测器。
 * ⚠ 只有<b>业务 4xx</b> 才这样收敛，下游故障照旧走 {@link BffFeignCall} 降级成 500——
 * 把「订单服务挂了」说成「订单不能评价」会让故障伪装成正常业务结果（同 {@code CatalogBffService}
 * 对「商品不存在」的处理）。</p>
 *
 * <p>⚠ <b>幂等由域侧唯一键保证</b>：同单同商品重复提交 → 域侧 400「该商品已评价」<b>原样透传</b>，
 * 本层不另做一次「查了再写」（那是两处判重，且挡不住并发）。</p>
 *
 * <p>⚠ <b>本层无星级筛选</b>：C 端评价区只有时间倒序 + 星级<b>分布</b>（分布是展示、筛选是操作，
 * 筛选是商户端的需求）。域侧分页接口两个条件都通用地提供，本层刻意不传 {@code scores}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationBffService {

    /** store 域熔断 / 连接异常降级提示 */
    private static final String DOWN_MSG = "评价暂不可用，请稍后重试";
    /** trade-center 熔断 / 连接异常降级提示（与 {@code OrderBffService} 同一句，出口文案保持一致） */
    private static final String ORDER_DOWN_MSG = "订单暂不可用，请稍后重试";

    /** 门禁失败的统一话术：不区分「订单不存在 / 不是你的单 / 状态未完成」 */
    private static final String GATE_MSG = "订单不存在或尚未完成，无法评价";

    /**
     * 可评价的订单状态名（域侧 {@code OrderStatus.RECEIVED}）。
     * <p>⚠ 用字面量而不是引域枚举：状态枚举在 trade-center 域内，契约包（{@code contract.trade}）
     * 只下发枚举<b>名</b>（见 {@code TradeOrderVO#getStatus}），不立第二份枚举——同
     * {@code MallOrderPageQueryDTO#getStatus} 的口径。</p>
     */
    private static final String STATUS_RECEIVED = "RECEIVED";

    /**
     * 拿不到顾客资料时的展示名占位。
     * <p>⚠ 它与写入侧默认昵称<b>同前缀、不同规则</b>：那条是「用户 + 手机号后 4 位」，且只在
     * <b>写入侧实现一处</b>（{@link CustomerProfileBffService#withDefaultNickname}）；这里是<b>纯占位</b>，
     * <b>不拼后 4 位</b>——手机号只在 {@code mall_user}（本端独有），商户端拿不到，两套算法必然漂移。
     * 前缀引用写入侧那一份，免得同一个字面量出现两次。</p>
     */
    private static final String PLACEHOLDER_NICKNAME = CustomerProfileBffService.NICKNAME_PREFIX;

    /** {@link BffFeignCall} 原样透传的那一类业务 4xx（400 参数/业务、403 权限、404 不存在） */
    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;

    private final TradeCenterClient tradeCenterClient;
    private final StoreClient storeClient;
    /** 顾客资料编排（customer-center）；⚠ 注入的是 BFF service <b>不是</b> CustomerCenterClient——
     *  「取不到资料要静默留空」是 customer-center 的失败语义，不该由本类去懂 */
    private final CustomerProfileBffService customerProfileBffService;

    /**
     * 提交商品评价：订单门禁 → 归组 SKU 快照 → 调 store 域写。
     *
     * @param customerId 评价人（<b>只能取自登录态</b>，见 controller）
     * @param dto        页面评价参数（订单号 / 商品 / 星级 / 文字）
     */
    public void submit(Long customerId, MallEvaluationSubmitDTO dto) {
        TradeOrderVO order = receivedOrderOrThrow(customerId, dto.getOrderNo());
        List<StoreGoodsEvaluationSkuVO> snapshot = skuSnapshotOf(order, dto.getSpuId());
        if (snapshot.isEmpty()) {
            // 订单是本人且已收货，这句话术只说「这单里没有这个商品」，不泄露别的东西
            throw new ServiceException("该订单不包含此商品，无法评价");
        }
        StoreGoodsEvaluationSubmitDTO payload = new StoreGoodsEvaluationSubmitDTO();
        payload.setOrderNo(order.getOrderNo());
        payload.setSpuId(dto.getSpuId());
        payload.setCustomerId(customerId);
        payload.setScore(dto.getScore());
        // 评价文字原样透传：空 / 空白的归一只在域侧一处做（本层再归一就是第二处口径）
        payload.setContent(dto.getContent());
        payload.setSkuSnapshot(snapshot);
        // 写操作不可降级：域侧业务 4xx（「该商品已评价」）原样透传，其余降级为「评价暂不可用」
        BffFeignCall.call("store", DOWN_MSG, () -> {
            storeClient.submitEvaluation(payload);
            return null;
        });
    }

    /**
     * 商品评价分页（固定时间倒序；每页条数由页面传）。
     *
     * <p>⚠ <b>一次批量补昵称 / 头像</b>（{@code listProfilesByIds}），不逐条取资料；
     * 资料读是<b>纯增强</b>：拿不到就整页退化为占位名「用户」、头像 {@code null}，
     * <b>绝不</b>因为 customer-center 不可用而让评价列表取不回来。</p>
     *
     * @param dto 页面分页参数（商品 id + 页码 / 每页条数）
     * @return 评价分页（本端私有形状 {@link MallEvaluationItemVO}）
     */
    public PageResult<MallEvaluationItemVO> page(MallEvaluationPageQueryDTO dto) {
        StoreGoodsEvaluationPageQueryDTO query = new StoreGoodsEvaluationPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setSpuId(dto.getSpuId());
        // storeId 不传（C 端是跨店视角，本页只按商品取）；scores 不传（C 端不提供星级筛选）
        PageResult<StoreGoodsEvaluationPageItemVO> raw = BffFeignCall.call("store", DOWN_MSG,
                () -> storeClient.pageEvaluations(query));

        List<StoreGoodsEvaluationPageItemVO> records =
                raw.getRecords() == null ? List.of() : raw.getRecords();
        // 一页最多 pageSize（≤100，见 BasePageVO）行，去重后只会更少 —— 落在批量接口 1~200 的窗口内
        Map<Long, CustomerProfileVO> profiles = customerProfileBffService.loadProfiles(customerIdsOf(records));

        PageResult<MallEvaluationItemVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(records.stream().map(item -> toItem(item, profiles)).toList());
        return result;
    }

    /**
     * 商品评价星级分布（1~5 星各多少人 + 总条数）。
     * <p>⚠ 出参<b>直接下发域类型</b> {@link StoreGoodsEvaluationStatVO}：它只有人数与总数，
     * <b>没有可裁剪 / 需补齐的字段</b>（固定 5 行由域侧保证），故不另造本端 VO
     * ——「同一份形状不造第二个出口」。</p>
     *
     * @param spuId 商品 SPU id（资源标识，不是作用域：任何登录顾客都能看任一商品的分布）
     * @return 星级分布
     */
    public StoreGoodsEvaluationStatVO stat(Long spuId) {
        StoreGoodsEvaluationStatQueryDTO query = new StoreGoodsEvaluationStatQueryDTO();
        query.setSpuId(spuId);
        // storeId 不传：C 端看的是「这个商品」，不分店铺
        return BffFeignCall.call("store", DOWN_MSG, () -> storeClient.evaluationStat(query));
    }

    /**
     * 取某订单里<b>已评价过</b>的商品 SPU id 集合（订单详情页标「已评价 / 待评价」用，一次取回不逐行调）。
     *
     * <p>⚠ <b>静默降级</b>（同 {@code OrderBffService#removeCartItemsQuietly} 与分类树那一类）：
     * store 域不可用时返回 {@code null} = <b>不下发已评价态</b>——订单详情的主内容不能因为
     * 「少一个增强标记」而没有了。入口照常可点，点了由<b>服务端兜</b>（域侧唯一键会拒）。
     * 故这条读<b>不走</b> {@link BffFeignCall}（它的语义是降级成 500 文案抛出去）。</p>
     *
     * <p>⚠ <b>{@code null} 与空集是两件事</b>：{@code null} = 拿不到（标记留空）；
     * 空集 = 确实一条都没评价（该单所有商品都还能评价）。调用方必须分开处理。</p>
     *
     * @param customerId 订单归属顾客（登录态；无单号归属校验时也顺手收窄）
     * @param orderNo    业务可读单号
     * @return 已评价的 spuId 集合；store 域不可用时为 {@code null}
     */
    public Set<Long> evaluatedSpuIdsQuietly(Long customerId, String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            StoreGoodsEvaluationOrderQueryDTO query = new StoreGoodsEvaluationOrderQueryDTO();
            query.setCustomerId(customerId);
            List<Long> ids = storeClient.listEvaluatedSpuIds(orderNo, query);
            return ids == null ? Set.of() : new LinkedHashSet<>(ids);
        } catch (Exception e) {
            // ⚠ catch 面刻意宽到 Exception：这是「订单详情已组装好之后」的增强读，漏网的异常会逃出
            //   详情接口让页面拿到 500 —— 而订单本身是好的。宁可不记全，也不能漏（同下单后清车的处理）
            log.warn("订单详情的已评价态获取失败，本次不下发该标记（增强读，静默降级）: customerId={}, orderNo={}",
                    customerId, orderNo, e);
            return null;
        }
    }

    // ---- 内部 ----

    /**
     * 订单门禁：取本人的单并校验状态为「已收货」；<b>任何不合格都回同一句 400</b>。
     * <p>⚠ 下游故障（熔断 / 连接等降级出来的 500）<b>原样抛出</b>，不收敛成门禁话术——否则顾客会以为
     * 是自己的订单不能评价，而实际是订单服务挂了。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态；同时是数据权限作用域）
     * @param orderNo    业务可读单号
     * @return 可评价的订单（状态已确认为 {@code RECEIVED}）
     */
    private TradeOrderVO receivedOrderOrThrow(Long customerId, String orderNo) {
        TradeOrderQueryDTO scope = new TradeOrderQueryDTO();
        scope.setCustomerId(customerId);
        TradeOrderVO order;
        try {
            order = BffFeignCall.call("trade-center", ORDER_DOWN_MSG,
                    () -> tradeCenterClient.getOrder(orderNo, scope));
        } catch (ServiceException e) {
            if (!isBusiness4xx(e)) {
                throw e;
            }
            // 域侧按 customerId 过滤：不存在与「不是你的单」都是 404，此处与「状态未完成」收敛成同一句话
            log.warn("评价门禁：订单取不到（不存在 / 不属本人），按不可评价处理: orderNo={}, msg={}",
                    orderNo, e.getMessage());
            throw new ServiceException(GATE_MSG);
        }
        if (order == null || !STATUS_RECEIVED.equals(order.getStatus())) {
            log.info("评价门禁：订单状态非已收货，拒绝评价: orderNo={}, status={}",
                    orderNo, order == null ? null : order.getStatus());
            throw new ServiceException(GATE_MSG);
        }
        return order;
    }

    /**
     * 从订单明细里归组该 SPU 的 <b>SKU 快照</b>（一单同一 SPU 的多个 SKU 行合成一条评价）；
     * 该单不含此 SPU 时返回空表。
     *
     * @param order 已收货的订单（明细非空）
     * @param spuId 被评价的商品 SPU id
     * @return SKU 快照行组（可空表）
     */
    private List<StoreGoodsEvaluationSkuVO> skuSnapshotOf(TradeOrderVO order, Long spuId) {
        List<StoreGoodsEvaluationSkuVO> snapshot = new ArrayList<>();
        if (order.getItems() == null) {
            return snapshot;
        }
        for (TradeOrderVO.Item item : order.getItems()) {
            if (item == null || item.getSpuId() == null || !item.getSpuId().equals(spuId)) {
                continue;
            }
            StoreGoodsEvaluationSkuVO row = new StoreGoodsEvaluationSkuVO();
            row.setSkuId(item.getSkuId());
            row.setSpecAttrs(toSpecAttrs(item.getSpecAttrs()));
            row.setUnitPrice(item.getUnitPrice());
            row.setQuantity(item.getQuantity());
            snapshot.add(row);
        }
        return snapshot;
    }

    /**
     * 规格从订单明细的 {@code Map}（规格名 → 取值）转成评价快照的 {@code List<SpecAttr>}。
     * <p>同一份事实的两种形状（订单侧按 map 存、评价快照按列表存），在对齐处集中转换一次。
     * 无规格 → 空表（不是 null，与 {@code StoreGoodsEvaluationSkuVO#getSpecAttrs} 的出参口径一致）。</p>
     *
     * @param specAttrs 订单明细行的规格（可空）
     * @return 规格属性列表（可空表）
     */
    private List<SpecAttr> toSpecAttrs(Map<String, String> specAttrs) {
        List<SpecAttr> attrs = new ArrayList<>();
        if (specAttrs == null || specAttrs.isEmpty()) {
            return attrs;
        }
        for (Map.Entry<String, String> entry : specAttrs.entrySet()) {
            SpecAttr attr = new SpecAttr();
            attr.setSpec(entry.getKey());
            attr.setValue(entry.getValue());
            attrs.add(attr);
        }
        return attrs;
    }

    /**
     * 取本页评价人去重后的账号 id（批量查资料用；空 / 全 null 时返回空表）
     *
     * @param records 本页评价
     * @return 去重后的顾客 id（保持出现顺序）
     */
    private Set<Long> customerIdsOf(List<StoreGoodsEvaluationPageItemVO> records) {
        Set<Long> ids = new LinkedHashSet<>();
        for (StoreGoodsEvaluationPageItemVO record : records) {
            if (record != null && record.getCustomerId() != null) {
                ids.add(record.getCustomerId());
            }
        }
        return ids;
    }

    /**
     * 域评价项 → C 端评价项（<b>逐字段手工映射</b>：域出参含 {@code customerId} 与 {@code spuName}，
     * 两项都不下发；同时把 {@code customerId} 换成昵称 / 头像）。
     *
     * @param src      域评价项
     * @param profiles 本次批量取回的顾客资料（可能不完整 / 为空）
     * @return C 端评价项
     */
    private MallEvaluationItemVO toItem(StoreGoodsEvaluationPageItemVO src,
                                       Map<Long, CustomerProfileVO> profiles) {
        MallEvaluationItemVO vo = new MallEvaluationItemVO();
        vo.setId(src.getId());
        vo.setSpuId(src.getSpuId());
        vo.setSkuSnapshot(src.getSkuSnapshot());
        vo.setScore(src.getScore());
        vo.setContent(src.getContent());
        vo.setCreateTime(src.getCreateTime());
        vo.setReplyContent(src.getReplyContent());
        vo.setReplyTime(src.getReplyTime());
        CustomerProfileVO profile = src.getCustomerId() == null ? null : profiles.get(src.getCustomerId());
        String nickname = profile == null ? null : profile.getNickname();
        // 拿不到资料 / 资料里昵称为空 → 统一占位（不拼手机号后 4 位，理由见 PLACEHOLDER_NICKNAME）
        vo.setNickname(StringUtils.hasText(nickname) ? nickname : PLACEHOLDER_NICKNAME);
        // 头像为空串也归一成 null：前端按 null 走默认头像，免得为空串再判一次（出参契约即「空 = null」）
        String avatar = profile == null ? null : profile.getAvatar();
        vo.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
        return vo;
    }

    /**
     * 判定是不是 {@link BffFeignCall} 原样透传的那类业务 4xx
     *
     * @param e 待判定异常
     * @return 400 / 403 / 404 之一为 true
     */
    private static boolean isBusiness4xx(ServiceException e) {
        Integer code = e.getCode();
        return code != null && (code == BAD_REQUEST || code == FORBIDDEN || code == NOT_FOUND);
    }
}
