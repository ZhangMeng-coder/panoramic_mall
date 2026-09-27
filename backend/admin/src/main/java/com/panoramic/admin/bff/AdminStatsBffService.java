package com.panoramic.admin.bff;

import com.panoramic.admin.dto.AdminStatsQueryDTO;
import com.panoramic.admin.vo.AdminStatsVO;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.stats.StatsDateRange;
import com.panoramic.common.stats.StatsGrain;
import com.panoramic.common.stats.StatsPointVO;
import com.panoramic.common.stats.StatsSeriesMerger;
import com.panoramic.common.stats.StatsWindows;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.dto.CustomerStatsQueryDTO;
import com.panoramic.contract.customer.vo.CustomerStatsPointVO;
import com.panoramic.contract.customer.vo.CustomerStatsVO;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.vo.ShopStatsVO;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeOrderStatsQueryDTO;
import com.panoramic.contract.trade.vo.TradeOrderStatsPointVO;
import com.panoramic.contract.trade.vo.TradeOrderStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * admin 端 BFF · 平台首页数据看板编排（**8 个指标挤在一个接口里**）。
 *
 * <p>⚠ <b>窗口算术与折线归并不在本类</b>：2026-09-27 起它们上收到 {@code common} 的
 * {@link StatsWindows} / {@link StatsSeriesMerger}，与店主端看板共用同一份实现
 * （cross-cutting 第 25 条：同一规则不写两遍）。本类只做「本端特有的那三件事」——
 * 组装三次域调用、算比例、把两个域的按天点映射成共用点类型。</p>
 *
 * <p>⚠ <b>本层只编排，不持任何域实体与表</b>：三项快照走 store / customer-center，
 * 五项窗口量走 customer-center / trade-center，全部经内部 Feign。</p>
 *
 * <p>⚠ <b>三次下游调用不各自降级</b>：判据是「首页要么全有要么全无」（一个接口承载八个指标的理由，
 * 见 {@code docs/contracts/admin.md}）——任一域不可用即整页失败 + 该域的友好文案，
 * 而不是把那一张卡片显示成 0（把「取不到数」说成「数是零」是更坏的失败）。
 * 业务 4xx（如自定义窗口不合法）由 {@link BffFeignCall} 原样透传。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsBffService {

    /** store 域熔断 / 连接异常降级提示 */
    private static final String STORE_DEGRADE_MSG = "店铺服务暂不可用，请稍后重试";

    /** customer-center 熔断 / 连接异常降级提示 */
    private static final String CUSTOMER_DEGRADE_MSG = "顾客服务暂不可用，请稍后重试";

    /** trade-center 熔断 / 连接异常降级提示 */
    private static final String ORDER_DEGRADE_MSG = "订单服务暂不可用，请稍后重试";

    /**
     * 成交比例保留的小数位。⚠ 回的是**比例（0~1）不是百分数**：乘 100 与加「%」是展示格式，
     * 属页面的事，BFF 不产出「已经乘过的数」。
     */
    private static final int RATIO_SCALE = 4;

    private final StoreClient storeClient;
    private final CustomerCenterClient customerCenterClient;
    private final TradeCenterClient tradeCenterClient;

    /**
     * 首页看板：解析窗口 → 三次取数 → 算比例 → 归并两条折线。
     *
     * @param dto 页面入参（窗口枚举 + 自定义起止）；{@code window} 为空按**本月**
     * @return 八个指标（快照 3 + 窗口 5，两条折线已按窗口零填充）
     */
    public AdminStatsVO overview(AdminStatsQueryDTO dto) {
        // 「今天」取**服务端**时钟（窗口按自然日历边界算，与浏览器时区无关）；
        // 缺省窗口（本月）与自定义窗口的校验都在 StatsWindows 里，本层不重述
        StatsDateRange range = StatsWindows.resolve(dto.getWindow(), dto.getStart(), dto.getEnd(), LocalDate.now());
        StatsGrain grain = StatsWindows.grainOf(range);

        ShopStatsVO shopStats = callStore(storeClient::getShopStats);
        CustomerStatsVO customerStats = callCustomer(() -> customerCenterClient.getCustomerStats(customerQuery(range)));
        TradeOrderStatsVO orderStats = callTrade(() -> tradeCenterClient.getOrderStats(orderQuery(range)));

        AdminStatsVO vo = new AdminStatsVO();
        // 三项快照：当前累计，与窗口无关（域侧接口也不收时间参数，第 25 条）
        vo.setUserCount(customerStats.getTotalCount());
        vo.setShopCount(shopStats.getShopCount());
        vo.setGoodsCount(shopStats.getGoodsCount());
        // 五项窗口量：营业额 / 成交数 / 分子分母取域，比例与两条折线在本层算
        vo.setRevenue(orderStats.getRevenue());
        vo.setDealOrderCount(orderStats.getDealOrderCount());
        vo.setDealOrderRatio(ratioOf(orderStats.getDealOrderCount(), orderStats.getPaidOrderCount()));
        vo.setGrain(grain);
        vo.setUserSeries(StatsSeriesMerger.merge(range, grain, userDailyPoints(customerStats.getNewSeries())));
        vo.setOrderSeries(StatsSeriesMerger.merge(range, grain, orderDailyPoints(orderStats.getNewSeries())));
        return vo;
    }

    // ---- 域点 → 共用点（两个域的同类点类型互不相关，故都映射到 common 的 StatsPointVO） ----

    /** 新增用户点：customer-center 的按天点 → 共用点 */
    private static List<StatsPointVO> userDailyPoints(List<CustomerStatsPointVO> points) {
        List<StatsPointVO> daily = new ArrayList<>(points.size());
        for (CustomerStatsPointVO point : points) {
            daily.add(toPoint(point.getDate(), point.getCount()));
        }
        return daily;
    }

    /** 新增订单点：trade-center 的按天点 → 共用点 */
    private static List<StatsPointVO> orderDailyPoints(List<TradeOrderStatsPointVO> points) {
        List<StatsPointVO> daily = new ArrayList<>(points.size());
        for (TradeOrderStatsPointVO point : points) {
            daily.add(toPoint(point.getDate(), point.getCount()));
        }
        return daily;
    }

    private static StatsPointVO toPoint(LocalDate date, Long count) {
        StatsPointVO point = new StatsPointVO();
        point.setDate(date);
        point.setCount(count);
        return point;
    }

    // ---- 派生量 ----

    /**
     * 成交比例 = 成交数 ÷ 窗口内已支付订单数。
     *
     * <p>⚠ 域只回**分子分母两个计数**（域不产出「已经除过的数」，第 25 条），除法与除零都在这。</p>
     *
     * <p>⚠ <b>分母为 0 时回 {@code null}（不是 0）</b>：窗口内一笔支付都没有时，
     * 「比例」没有定义；回 0 会把「没数据」说成「成交率 0%」。</p>
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

    // ---- Feign 组装 ----

    private static CustomerStatsQueryDTO customerQuery(StatsDateRange range) {
        CustomerStatsQueryDTO query = new CustomerStatsQueryDTO();
        query.setStart(range.start());
        query.setEnd(range.end());
        return query;
    }

    private static TradeOrderStatsQueryDTO orderQuery(StatsDateRange range) {
        TradeOrderStatsQueryDTO query = new TradeOrderStatsQueryDTO();
        query.setStart(range.start());
        query.setEnd(range.end());
        return query;
    }

    /** 调 store 的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}） */
    private <T> T callStore(Supplier<RespData<T>> action) {
        return BffFeignCall.call("store", STORE_DEGRADE_MSG, action);
    }

    /** 调 customer-center 的统一编排执行 */
    private <T> T callCustomer(Supplier<RespData<T>> action) {
        return BffFeignCall.call("customer-center", CUSTOMER_DEGRADE_MSG, action);
    }

    /** 调 trade-center 的统一编排执行 */
    private <T> T callTrade(Supplier<RespData<T>> action) {
        return BffFeignCall.call("trade-center", ORDER_DEGRADE_MSG, action);
    }
}
