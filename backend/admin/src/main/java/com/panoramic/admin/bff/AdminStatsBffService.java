package com.panoramic.admin.bff;

import com.panoramic.admin.dto.AdminStatsQueryDTO;
import com.panoramic.admin.vo.AdminStatsPointVO;
import com.panoramic.admin.vo.AdminStatsVO;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * admin 端 BFF · 平台首页数据看板编排（**8 个指标挤在一个接口里**）。
 *
 * <p><b>本类是全系统「时间窗口」与「折线粒度」的唯一实现处</b>（cross-cutting 第 25 条）：
 * 三个域只收显式 {@code start} / {@code end}、一律按天出点；「本月 / 上季 / 今年」的日历算术与
 * 「按天 → 按月」的归并全在这里。⚠ 别把窗口枚举或粒度沉到域里 —— 那会让同一套月 / 季 / 年算术
 * 在每个域各存一份、各自漂移，且漂移**不报错**（只是首页几个数字彼此对不上）。</p>
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
     * 折线粒度的分界线（**含**）：窗口跨度 ≤ 180 天按天出点，&gt; 180 天按月归并。
     *
     * <p>⚠ 180 而不是更小的数：本季 / 上季最长 92 天，按天出点正好放得下（≤ 92 个点），
     * 而「今年 / 去年」必须按月——否则 365 个点在图上糊成一片。取 62 会让本季退化成 3 个点，太粗。</p>
     */
    private static final int DAILY_MAX_SPAN_DAYS = 180;

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
        // 缺省 = 本月：页面默认选中的也是本月，两端同口径；页面显式传时以传入为准
        AdminStatsQueryDTO.Window window = dto.getWindow() == null
                ? AdminStatsQueryDTO.Window.THIS_MONTH
                : dto.getWindow();
        // 「今天」取**服务端**时钟（窗口按自然日历边界算，与浏览器时区无关）
        DateRange range = resolveRange(window, dto, LocalDate.now());
        AdminStatsVO.Grain grain = grainOf(range);

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
        vo.setUserSeries(mergeSeries(range, grain, userDailyPoints(customerStats.getNewSeries())));
        vo.setOrderSeries(mergeSeries(range, grain, orderDailyPoints(orderStats.getNewSeries())));
        return vo;
    }

    // ---- 窗口解析（全系统唯一一处） ----

    /**
     * 窗口枚举 + 自定义起止 → 显式的闭区间 {@code [start, end]}。
     *
     * <p>⚠ <b>全部按自然日历边界</b>：本月 = 当月 1 号至月末，**不是**「最近 30 天」。
     * 闭区间含首尾整天——域侧把上界取成 {@code end} 次日 00:00 并用 {@code <} 排除，
     * 故这里给出 {@code end} 当天即可，**不必也不能**给出 {@code 23:59:59}
     * （域接口收的是日期不是时刻，转时刻是各域自己的事）。</p>
     *
     * <p>⚠ 本季 / 上季**不做降级**：季度边界就是 {@code (month - 1) / 3 * 3 + 1} 的几行算术。</p>
     *
     * @param dto 页面入参；非 {@link AdminStatsQueryDTO.Window#CUSTOM} 时其 {@code start} / {@code end} **被忽略**
     *            （页面若把上次自定义的值一并传了，不该污染枚举窗口）
     */
    private static DateRange resolveRange(AdminStatsQueryDTO.Window window, AdminStatsQueryDTO dto, LocalDate today) {
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        return switch (window) {
            case THIS_MONTH -> new DateRange(thisMonthStart, thisMonthStart.plusMonths(1).minusDays(1));
            case LAST_MONTH -> lastMonthOf(thisMonthStart);
            case THIS_QUARTER -> quarterOf(today);
            // 上季末月最后一天 = 本季首日前一天；起点再往前推 3 个月
            case LAST_QUARTER -> {
                LocalDate quarterStart = quarterOf(today).start();
                yield new DateRange(quarterStart.minusMonths(3), quarterStart.minusDays(1));
            }
            case THIS_YEAR -> new DateRange(LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31));
            case LAST_YEAR -> new DateRange(LocalDate.of(today.getYear() - 1, 1, 1),
                    LocalDate.of(today.getYear() - 1, 12, 31));
            case CUSTOM -> customRange(dto);
        };
    }

    /**
     * 自定义窗口：起止**都必填**、且不得倒挂。
     *
     * <p>⚠ 两个字段在 {@link AdminStatsQueryDTO} 上是可空的（只有自定义窗口才用它们），
     * 故必填性**只能在这里判**——这与「写侧作用域必填由 DTO 上的 {@code @NotNull} 守」的做法不同，
     * 因为那两个字段在别的窗口下本来就该缺省。</p>
     *
     * @throws ServiceException 缺一端、或结束早于开始（回 400，文案可直接展示）
     */
    private static DateRange customRange(AdminStatsQueryDTO dto) {
        LocalDate start = dto.getStart();
        LocalDate end = dto.getEnd();
        if (start == null || end == null) {
            throw new ServiceException("自定义时间窗口必须同时给出起止日期");
        }
        if (end.isBefore(start)) {
            throw new ServiceException("时间窗口不合法：结束日期早于开始日期");
        }
        return new DateRange(start, end);
    }

    /** 上月：以「本月 1 号」为锚往回推，跨年由 {@code minusMonths} 自己处理 */
    private static DateRange lastMonthOf(LocalDate thisMonthStart) {
        LocalDate lastMonthStart = thisMonthStart.minusMonths(1);
        return new DateRange(lastMonthStart, lastMonthStart.plusMonths(1).minusDays(1));
    }

    /** 本季：首月 = {@code (month - 1) / 3 * 3 + 1}（1 / 4 / 7 / 10），末月最后一天 = 首日 + 3 个月 - 1 天 */
    private static DateRange quarterOf(LocalDate today) {
        int firstMonthOfQuarter = (today.getMonthValue() - 1) / 3 * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), firstMonthOfQuarter, 1);
        return new DateRange(start, start.plusMonths(3).minusDays(1));
    }

    /** 跨度 ≤ {@value #DAILY_MAX_SPAN_DAYS} 天按天，否则按月（闭区间，两端都算） */
    private static AdminStatsVO.Grain grainOf(DateRange range) {
        long spanDays = ChronoUnit.DAYS.between(range.start(), range.end()) + 1;
        return spanDays <= DAILY_MAX_SPAN_DAYS ? AdminStatsVO.Grain.DAY : AdminStatsVO.Grain.MONTH;
    }

    // ---- 折线归并（按天 → 按月 + 窗口零填充） ----

    /**
     * 把域回的**稀疏按天点**摊到窗口的完整桶轴上：窗口内每个桶都有一项（没数据的桶 = 0）。
     *
     * <p>⚠ 补零只能在**本层**做：「窗口从哪天到哪天」是调用方的概念，域只回有数据的日期
     * （见域侧点类型的注释），它连「该不该补到月末」都不知道。</p>
     *
     * <p>⚠ 桶的**顺序即轴顺序**（{@code LinkedHashMap} 按窗口依次铺开），页面可直接照序画线。</p>
     *
     * <p>⚠ 域按同一个窗口查询，理论上一日一点都落在窗口内；真出现窗外点也**不静默丢**——
     * 它会作为一个多出来的桶出现在轴上（静默丢弃会让页面上少一个数而不报错）。</p>
     */
    private static List<AdminStatsPointVO> mergeSeries(DateRange range, AdminStatsVO.Grain grain,
                                                       List<DailyPoint> daily) {
        Map<LocalDate, Long> buckets = emptyBuckets(range, grain);
        for (DailyPoint point : daily) {
            buckets.merge(bucketOf(point.date(), grain), point.count(), Long::sum);
        }
        List<AdminStatsPointVO> series = new ArrayList<>(buckets.size());
        for (Map.Entry<LocalDate, Long> bucket : buckets.entrySet()) {
            AdminStatsPointVO vo = new AdminStatsPointVO();
            vo.setDate(bucket.getKey());
            vo.setCount(bucket.getValue());
            series.add(vo);
        }
        return series;
    }

    /** 按窗口铺开全部空桶（值为 0），顺序即坐标轴顺序 */
    private static Map<LocalDate, Long> emptyBuckets(DateRange range, AdminStatsVO.Grain grain) {
        Map<LocalDate, Long> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = bucketOf(range.start(), grain);
             !cursor.isAfter(range.end());
             cursor = nextBucket(cursor, grain)) {
            buckets.put(cursor, 0L);
        }
        return buckets;
    }

    /** 某一天落进哪个桶：按天 = 它自己；按月 = 当月 1 号（月桶的标识） */
    private static LocalDate bucketOf(LocalDate date, AdminStatsVO.Grain grain) {
        return grain == AdminStatsVO.Grain.DAY ? date : date.withDayOfMonth(1);
    }

    private static LocalDate nextBucket(LocalDate bucket, AdminStatsVO.Grain grain) {
        return grain == AdminStatsVO.Grain.DAY ? bucket.plusDays(1) : bucket.plusMonths(1);
    }

    /** 新增用户点：域的按天点 → 本类的中性点（两个域的同类点类型无关，故在本层统一） */
    private static List<DailyPoint> userDailyPoints(List<CustomerStatsPointVO> points) {
        List<DailyPoint> daily = new ArrayList<>(points.size());
        for (CustomerStatsPointVO point : points) {
            daily.add(new DailyPoint(point.getDate(), point.getCount()));
        }
        return daily;
    }

    /** 新增订单点：域的按天点 → 本类的中性点（同上） */
    private static List<DailyPoint> orderDailyPoints(List<TradeOrderStatsPointVO> points) {
        List<DailyPoint> daily = new ArrayList<>(points.size());
        for (TradeOrderStatsPointVO point : points) {
            daily.add(new DailyPoint(point.getDate(), point.getCount()));
        }
        return daily;
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

    private static CustomerStatsQueryDTO customerQuery(DateRange range) {
        CustomerStatsQueryDTO query = new CustomerStatsQueryDTO();
        query.setStart(range.start());
        query.setEnd(range.end());
        return query;
    }

    private static TradeOrderStatsQueryDTO orderQuery(DateRange range) {
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

    /**
     * 窗口闭区间（两端都含当天）
     */
    private record DateRange(LocalDate start, LocalDate end) {
    }

    /**
     * 域回的按天点在**本层**的中性形状：两个域的同类点类型互不相关（一个在 customer 契约包、
     * 一个在 trade 契约包），归并逻辑不该为此写两遍，故先映射成同一个内部记录。
     */
    private record DailyPoint(LocalDate date, Long count) {
    }
}
