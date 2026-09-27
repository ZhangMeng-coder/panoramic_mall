package com.panoramic.common.stats;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把域回的**稀疏按天点**摊到窗口的完整桶轴上（按天 → 按月归并 + 窗口零填充）。
 *
 * <p>⚠ 补零只能在 BFF 做：「窗口从哪天到哪天」是**调用方**的概念，域只回有数据的日期
 * （见域侧点类型的注释），它连「该不该补到月末」都不知道。</p>
 *
 * <p>⚠ 桶的**顺序即坐标轴顺序**（{@link LinkedHashMap} 按窗口依次铺开），页面可直接照序画线。</p>
 */
public final class StatsSeriesMerger {

    private StatsSeriesMerger() {
    }

    /**
     * 归并成窗口内的完整桶序列：每个桶都有一项（没数据的桶 = 0）。
     *
     * <p>⚠ 域按同一个窗口查询，理论上一日一点都落在窗口内；真出现窗外点也**不静默丢**——
     * 它会作为一个多出来的桶出现在轴上（静默丢弃会让页面上少一个数而不报错）。</p>
     *
     * @param range 窗口闭区间（决定桶轴的两端）
     * @param grain 粒度（按天一天一桶 / 按月一月一桶）
     * @param daily 域回的按天点（可为空列表，不可为 {@code null}）
     * @return 按轴顺序排好的桶序列；窗口内无数据时是一串 0 而不是空列表
     */
    public static List<StatsPointVO> merge(StatsDateRange range, StatsGrain grain, List<StatsPointVO> daily) {
        Map<LocalDate, Long> buckets = emptyBuckets(range, grain);
        for (StatsPointVO point : daily) {
            // ⚠ 不对 null count 兜底：域回的计数是聚合结果、本就不该是 null；
            // 真为 null 就让 Long::sum NPE 当场炸出来，别静默补成 0（那是把「取不到数」说成「数是零」）
            buckets.merge(bucketOf(point.getDate(), grain), point.getCount(), Long::sum);
        }
        List<StatsPointVO> series = new ArrayList<>(buckets.size());
        for (Map.Entry<LocalDate, Long> bucket : buckets.entrySet()) {
            StatsPointVO vo = new StatsPointVO();
            vo.setDate(bucket.getKey());
            vo.setCount(bucket.getValue());
            series.add(vo);
        }
        return series;
    }

    /** 按窗口铺开全部空桶（值为 0），顺序即坐标轴顺序 */
    private static Map<LocalDate, Long> emptyBuckets(StatsDateRange range, StatsGrain grain) {
        Map<LocalDate, Long> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = bucketOf(range.start(), grain);
             !cursor.isAfter(range.end());
             cursor = nextBucket(cursor, grain)) {
            buckets.put(cursor, 0L);
        }
        return buckets;
    }

    /** 某一天落进哪个桶：按天 = 它自己；按月 = 当月 1 号（月桶的标识） */
    private static LocalDate bucketOf(LocalDate date, StatsGrain grain) {
        return grain == StatsGrain.DAY ? date : date.withDayOfMonth(1);
    }

    private static LocalDate nextBucket(LocalDate bucket, StatsGrain grain) {
        return grain == StatsGrain.DAY ? bucket.plusDays(1) : bucket.plusMonths(1);
    }
}
