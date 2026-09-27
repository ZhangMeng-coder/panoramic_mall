package com.panoramic.store.rank;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 排序分算法（{@code store_goods_spu.rank_score}）：<b>评分 / 销量 / 价格</b>三个分项各归一到
 * 0~100 后加权求和，值域 0.00 ~ 100.00，越高越靠前。
 *
 * <h3>为什么每个维度都必须「绝对归一化」</h3>
 * <p>需求要求「<b>对该商品</b>进行重算」（价格变 / 评分变 / 销量变时只重算那一个 SPU）。
 * 若某一维用全库 min-max 归一化，任何一个商品变化都会改变其它所有商品的分，重算就退化成「全库重算」，
 * 「价格变化只重算该商品」当场不成立。故三个维度一律用<b>与其它商品无关的绝对映射</b>
 * （饱和式截断 / 常数比值）——单商品随时可独立算，算分无需查其它行。</p>
 *
 * <h3>三个分项（常数见下方常量）</h3>
 * <table>
 *   <tr><th>维度</th><th>映射</th><th>权重</th></tr>
 *   <tr><td>销量 {@code sales_count}</td>
 *       <td>越多越高，<b>饱和式</b>：{@code 100 * min(1, sales / SALES_FULL)}，满 {@code SALES_FULL} 件即满分</td>
 *       <td>40%</td></tr>
 *   <tr><td>评分 {@code score}</td>
 *       <td>{@code 100 * score / 5}；<b>NULL（无评价）取中性 {@code SCORE_NEUTRAL}</b></td>
 *       <td>35%</td></tr>
 *   <tr><td>价格 {@code min_price}</td>
 *       <td><b>越低越高</b>且<b>幂律压缩</b>：{@code 100 * (PRICE_FLOOR / max(min_price, PRICE_FLOOR))}；
 *           NULL（无上架 SKU）取中性 {@code PRICE_NEUTRAL}</td>
 *       <td>25%</td></tr>
 * </table>
 * <p>⚠ 价格用 {@code FLOOR / price} 而不是线性，是因为低价段的差别远大于高价段
 * （1 元与 30 元的差别 ≫ 300 与 330 的差别）——线性映射会把「便宜」这一维在高价段压平。</p>
 *
 * <h3>缺数据取中性值（需求「默认值计算一个大概的中位数」）</h3>
 * <p>新商品没有销量、没有评分，<b>不能因此永远排最后</b>：评分与价格缺数据时取中性值
 * （{@code SCORE_NEUTRAL = 60} 恰是 3 星、{@code PRICE_NEUTRAL = 50} 是不好不坏），
 * 算出来的分天然落在中位数附近。⚠ 销量<b>没有中性值</b>——{@code sales_count} 恒有值
 * （{@code NOT NULL DEFAULT 0}），0 件就是 0 分，这是「新品排后面」的正常表现，不是缺数据。</p>
 *
 * <p>纯函数、无状态、不依赖 Spring：调用方是 {@code StoreGoodsSpuServiceImpl#recalculateRank}
 * （异步重算与兜底扫描都经它），本类<b>不读库</b>，故可被单测直接钉住。</p>
 */
public final class RankCalculator {

    /** 销量权重（%） */
    private static final int WEIGHT_SALES = 40;
    /** 评分权重（%） */
    private static final int WEIGHT_SCORE = 35;
    /** 价格权重（%） */
    private static final int WEIGHT_PRICE = 25;

    /**
     * 销量维度满分对应的件数（饱和点）：卖出这么多件即得满分，此后不再影响分数。
     * <p>⚠ 饱和是刻意的：若不封顶，一个爆款会把所有商品挤到后面，排序退化成「销量榜」。
     * 口径一旦抬高（如 500），「销量」这一维的实际区分度会整体下移。</p>
     */
    private static final int SALES_FULL = 50;

    /** 价格维度满分对应的人民币金额：≤ 它即得满分；同时也是除数下限（防 0 价除零） */
    private static final BigDecimal PRICE_FLOOR = new BigDecimal("10");

    /** 评分满分（5 星） */
    private static final BigDecimal SCORE_MAX = new BigDecimal("5");

    /**
     * 评分缺失时的中性分项（0~100 口径）：60 = 3 星，即「不好不坏」。
     * <p>取 60 而不是 50，是因为评分天然偏高（1 星罕见），中位数落在 3~4 星之间。</p>
     */
    private static final BigDecimal SCORE_NEUTRAL = new BigDecimal("60");

    /**
     * 价格缺失时的中性分项（无上架 SKU，如商品下架）：50。
     * <p>⚠ 这类商品不在 C 端列表里（C 端固定 {@code shelfStatus=1}），取哪个值都不会被消费；
     * 给中性值只是为了让「不消费的分」看起来正常，而不是 0 分那种像个 bug 的值。</p>
     */
    private static final BigDecimal PRICE_NEUTRAL = new BigDecimal("50");

    /** 分项的 0~100 口径满分 */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 分项与最终结果的小数位（与 {@code DECIMAL(5,2)} 一致） */
    private static final int SCALE = 2;

    private RankCalculator() {
    }

    /**
     * 算一个 SPU 的排序分（单商品独立计算，不查其它行）。
     *
     * @param salesCount 累计销量（订单完成口径；null / 负数按 0 计）
     * @param score      商品评分（0~5，null = 尚无评价 → 取中性值）
     * @param minPrice   在售 SKU 最低价（null = 无上架 SKU → 取中性值）
     * @return 排序分（0.00 ~ 100.00，两位小数）
     */
    public static BigDecimal compute(Integer salesCount, BigDecimal score, BigDecimal minPrice) {
        BigDecimal total = salesPart(salesCount).multiply(BigDecimal.valueOf(WEIGHT_SALES))
                .add(scorePart(score).multiply(BigDecimal.valueOf(WEIGHT_SCORE)))
                .add(pricePart(minPrice).multiply(BigDecimal.valueOf(WEIGHT_PRICE)));
        // 三个权重之和恒为 100，故除以 100 即回到 0~100 口径
        return total.divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 销量分项：{@code 100 * min(1, sales / SALES_FULL)}
     */
    private static BigDecimal salesPart(Integer salesCount) {
        int sales = (salesCount == null || salesCount < 0) ? 0 : salesCount;
        if (sales >= SALES_FULL) {
            return HUNDRED;
        }
        return BigDecimal.valueOf(sales)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(SALES_FULL), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 评分分项：{@code 100 * score / 5}，缺失取中性值
     */
    private static BigDecimal scorePart(BigDecimal score) {
        if (score == null) {
            return SCORE_NEUTRAL;
        }
        // 评分由评价服务写入（1~5 星），此处仍做一次上下界收敛：脏数据不该算出超出值域的分
        BigDecimal clamped = score.min(SCORE_MAX).max(BigDecimal.ZERO);
        return clamped.multiply(HUNDRED).divide(SCORE_MAX, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 价格分项：{@code 100 * (PRICE_FLOOR / max(min_price, PRICE_FLOOR))}，缺失取中性值
     */
    private static BigDecimal pricePart(BigDecimal minPrice) {
        if (minPrice == null) {
            return PRICE_NEUTRAL;
        }
        // 取下限即为除数兜底：≤ 10 元（含 0 价这种脏数据）一律满分，且不可能除零
        BigDecimal effective = minPrice.max(PRICE_FLOOR);
        return HUNDRED.multiply(PRICE_FLOOR).divide(effective, SCALE, RoundingMode.HALF_UP);
    }
}
