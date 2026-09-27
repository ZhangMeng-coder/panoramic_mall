package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.stats.StatsDateRange;
import com.panoramic.common.stats.StatsGrain;
import com.panoramic.common.stats.StatsPointVO;
import com.panoramic.common.stats.StatsSeriesMerger;
import com.panoramic.common.stats.StatsWindows;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationStatQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStatsQueryDTO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationStatVO;
import com.panoramic.contract.store.vo.StoreGoodsStatsVO;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeOrderStatsQueryDTO;
import com.panoramic.contract.trade.vo.TradeOrderStatsPointVO;
import com.panoramic.contract.trade.vo.TradeOrderStatsVO;
import com.panoramic.storebff.dto.StoreStatsQueryDTO;
import com.panoramic.storebff.vo.StoreStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 店铺端 BFF · 店主端首页看板编排（8 个指标一个接口）。
 * <p>本层<b>不落表、不算指标</b>：三个计数在 store 域（{@code StoreGoodsStatsVO}）、
 * 评价分布在 store 域（{@code evaluationStat}）、四个订单口径在 trade-center
 * （{@code getOrderStats}）；本层只做<b>三件域做不到的事</b>——</p>
 * <ol>
 *   <li><b>作用域</b>：{@link #currentStoreId()} 从登录态取店主账号 id（== store_id），
 *       <b>无条件</b>写进三个域入参 DTO。页面入参里<b>不含</b> {@code storeId}
 *       （{@link StoreStatsQueryDTO}）——页面能传来的锚点等于把数据权限交给页面（cross-cutting 第 22 条）。</li>
 *   <li><b>窗口</b>：{@code window} 枚举 + 自定义起止 → 显式 {@code [start, end]}，以及粒度。
 *       用的是 {@code common} 的 {@link StatsWindows}（<b>本层不写月 / 季 / 年算术</b>，
 *       第 25 条），与平台看板 {@code AdminStatsBffService} 同一份实现。</li>
 *   <li><b>比率与折线的补零归并</b>：比率是展示层派生量、域只回分子分母；折线要摊到窗口的完整桶轴上
 *       （{@link StatsSeriesMerger}；「窗口从哪天到哪天」只有本层知道）。</li>
 * </ol>
 *
 * <p>⚠ <b>前 3 项快照指标不带窗口</b>：上架 / 下架 / 库存异常都是「本店现在有多少」，
 * 对应的域接口<b>不收任何时间参数</b>——别为了和下面 5 项对称给它也塞一个窗口（第 25 条）。</p>
 *
 * <p>⚠ <b>本接口不套审核门禁</b>（与同层的 {@code /goods/**} 相反）：店主端首页不在「开店后业务入口」
 * 那组页面里，未开店 / 未过审同样打得开首页。故本层<b>不查店铺状态</b>、不因「无店」报错：
 * 三个域调用都只按 {@code storeId} 过滤，空店天然回 0 与零填充序列（折线是一串 0、<b>不是空数组</b>），
 * <b>不是</b> 404、<b>不是</b> 403、<b>不是</b>降级 500。⚠ 本层也<b>没有</b> {@code @PreAuthorize}
 * （店主端不接 RBAC，登录态是唯一门槛）。</p>
 *
 * <p>下游业务异常（400 / 403 / 404）沿 cause 链剥出后原样透传；只有熔断 / 连接 / 序列化才降级为
 * 友好提示，该逻辑已抽到 common 的 {@link BffFeignCall}，本类只传降级文案（store 域与 trade-center
 * 各一句，别混用）。</p>
 */
@Service
@RequiredArgsConstructor
public class StoreStatsBffService {

    /** store 域熔断 / 连接异常降级提示（商品计数与评价分布共用） */
    private static final String STORE_DEGRADE_MSG = "店铺服务暂不可用，请稍后重试";

    /** trade-center 熔断 / 连接异常降级提示 */
    private static final String ORDER_DEGRADE_MSG = "订单服务暂不可用，请稍后重试";

    /**
     * 比率保留 4 位小数（与平台看板 {@code AdminStatsBffService} 同一档——两端同指标同精度，
     * 页面显示的百分比才不会有第 3 位上的差异）
     */
    private static final int RATIO_SCALE = 4;

    private final StoreClient storeClient;
    private final TradeCenterClient tradeCenterClient;

    /**
     * 店主端首页看板：3 个快照计数（本店商品规模）+ 5 个窗口指标（营业额 / 成交数 / 成交比例 /
     * 评价分布 / 订单折线）。
     *
     * @param dto 页面入参（只有时间窗口，<b>没有</b> storeId）
     * @return 8 个指标（计数无则 0、比率分母为 0 则 {@code null}、折线已按窗口零填充）
     * @throws ServiceException 自定义窗口缺一端 / 倒挂（400）；登录态失效（400）；
     *                          两个下游之一不可用（500 降级文案，见 {@link BffFeignCall}）
     */
    public StoreStatsVO overview(StoreStatsQueryDTO dto) {
        Long storeId = currentStoreId();
        StatsDateRange range = StatsWindows.resolve(dto.getWindow(), dto.getStart(), dto.getEnd(), LocalDate.now());
        StatsGrain grain = StatsWindows.grainOf(range);

        // 作用域三个域调用一起带（本店看板四个数必须同源，否则加总对不上）
        StoreGoodsStatsVO goodsStats = callStore(() -> storeClient.getGoodsStats(goodsQuery(storeId)));
        StoreGoodsEvaluationStatVO evaluationStat =
                callStore(() -> storeClient.evaluationStat(evaluationQuery(range, storeId)));
        TradeOrderStatsVO orderStats =
                callOrder(() -> tradeCenterClient.getOrderStats(orderQuery(range, storeId)));

        StoreStatsVO vo = new StoreStatsVO();
        // 快照三项：直接搬运，本层不重算（口径在 store 域的三个 owner 方法上）
        vo.setOnShelfCount(goodsStats.getOnShelfCount());
        vo.setOffShelfCount(goodsStats.getOffShelfCount());
        vo.setAbnormalStockCount(goodsStats.getAbnormalStockCount());
        // 窗口五项：订单三个数 + 比率（本层算）+ 评价分布 + 折线（本层补零归并）
        vo.setRevenue(orderStats.getRevenue());
        vo.setDealOrderCount(orderStats.getDealOrderCount());
        vo.setDealOrderRatio(ratioOf(orderStats.getDealOrderCount(), orderStats.getPaidOrderCount()));
        // 星级分布不补零也不归并：域侧恒回 1~5 五行（见 StoreGoodsEvaluationStatVO），
        // 它是一条固定轴上的柱状图，与折线的「桶轴」不是一回事
        vo.setEvaluationScores(evaluationStat.getScores());
        vo.setGrain(grain);
        vo.setOrderSeries(StatsSeriesMerger.merge(range, grain, orderDailyPoints(orderStats.getNewSeries())));
        return vo;
    }

    // ---- 域入参组装（作用域与窗口都在这里落到域 DTO 上） ----

    /** 商品计数入参：只有作用域，<b>没有时间字段</b>（快照接口不收窗口） */
    private static StoreGoodsStatsQueryDTO goodsQuery(Long storeId) {
        StoreGoodsStatsQueryDTO query = new StoreGoodsStatsQueryDTO();
        query.setStoreId(storeId);
        return query;
    }

    /** 评价分布入参：作用域 + 窗口两个起止（域侧「不传 = 不限定」，本层恒传窗口） */
    private static StoreGoodsEvaluationStatQueryDTO evaluationQuery(StatsDateRange range, Long storeId) {
        StoreGoodsEvaluationStatQueryDTO query = new StoreGoodsEvaluationStatQueryDTO();
        query.setStoreId(storeId);
        query.setStart(range.start());
        query.setEnd(range.end());
        return query;
    }

    /** 订单统计入参：作用域 + 窗口两个起止（四个部件一起筛） */
    private static TradeOrderStatsQueryDTO orderQuery(StatsDateRange range, Long storeId) {
        TradeOrderStatsQueryDTO query = new TradeOrderStatsQueryDTO();
        query.setStoreId(storeId);
        query.setStart(range.start());
        query.setEnd(range.end());
        return query;
    }

    // ---- 派生量与映射 ----

    /**
     * 成交比例 = 成交数 ÷ 已支付订单数。
     *
     * <p>⚠ <b>分母为 0 回 {@code null} 而不是 0</b>：窗口内一笔都没付过款时，「成交比例」无定义，
     * 页面展示「—」；回 0 会让页面把它画成 0%（把「没数据」说成「数据是零」）。
     * 口径与平台看板逐字同义（{@code AdminStatsBffService#ratioOf}）。</p>
     */
    private static BigDecimal ratioOf(Long dealOrderCount, Long paidOrderCount) {
        long denominator = paidOrderCount == null ? 0L : paidOrderCount;
        if (denominator == 0L) {
            return null;
        }
        long numerator = dealOrderCount == null ? 0L : dealOrderCount;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** 新增订单点：trade-center 的按天点 → 共用点（两个域的同类点互不相关，故都映射到 common 的形状） */
    private static List<StatsPointVO> orderDailyPoints(List<TradeOrderStatsPointVO> points) {
        List<StatsPointVO> daily = new ArrayList<>(points.size());
        for (TradeOrderStatsPointVO point : points) {
            StatsPointVO vo = new StatsPointVO();
            vo.setDate(point.getDate());
            vo.setCount(point.getCount());
            daily.add(vo);
        }
        return daily;
    }

    /**
     * 取当前登录店主账号 id（== store_id），登录态缺失时拒绝
     * <p>⚠ 与 {@code StoreEvaluationBffService#currentStoreId()} 同一条规则（本条口径全系统只有一份，
     * 但两处都短，各自持有一份私有实现而不是互相依赖——BFF 之间不互相调用）。</p>
     */
    private Long currentStoreId() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException("登录已失效，请重新登录");
        }
        return loginUser.getId();
    }

    /**
     * 调 store 域的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callStore(Supplier<RespData<T>> action) {
        return BffFeignCall.call("store", STORE_DEGRADE_MSG, action);
    }

    /**
     * 调 trade-center 的统一编排执行（降级文案与 store 域那句分开，页面提示才指向对的下游）
     */
    private <T> T callOrder(Supplier<RespData<T>> action) {
        return BffFeignCall.call("trade-center", ORDER_DEGRADE_MSG, action);
    }
}
