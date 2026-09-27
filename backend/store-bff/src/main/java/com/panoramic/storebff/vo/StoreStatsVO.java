package com.panoramic.storebff.vo;

import com.panoramic.common.stats.StatsGrain;
import com.panoramic.common.stats.StatsPointVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationScoreCountVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 店主端首页看板（**8 个指标挤在一个接口里**，见 {@code docs/contracts/store-bff.md}）。
 *
 * <p>首页一次加载要么全有要么全无，拆成 4 个接口只会带来 4 次往返、4 份 loading 态。
 * 与平台看板（{@code AdminStatsVO}）<b>刻意同形</b>：前 3 项是当前累计快照，其余 5 项按窗口，
 * 页面结构与时间窗口选择器两端共用一套做法。</p>
 *
 * <p>⚠ <b>指标的时间基准分两类，不是笔误</b>：
 * {@link #onShelfCount} / {@link #offShelfCount} / {@link #abnormalStockCount} 是<b>当前累计快照</b>
 * （不受窗口影响、也不受入参影响）；其余 5 个按窗口算。</p>
 *
 * <p>⚠ <b>三个快照计数的粒度不同、互不可比</b>：前两个数的是 <b>SPU</b>，库存异常数的是 <b>SKU 行</b>
 * ——别拿「上架 + 下架」或「下架 = 库存异常」之类的等式去理解它（口径钉在 store 域的三个 owner 方法上，
 * 见 {@code backend/store/README.md} 第 9 节）。</p>
 *
 * <p>⚠ 折线点与粒度用的是 {@code common} 的共用类型（{@link StatsPointVO} / {@link StatsGrain}），
 * 与平台看板同一份——它们是「BFF 补零归并之后的点」，与域侧的 {@code TradeOrderStatsPointVO}
 * <b>不可互相替换</b>。</p>
 *
 * <p>⚠ <b>各指标口径属「业务规则」</b>（谁的窗口、评价分布按不按窗口、库存异常怎么判），
 * 见 {@code backend/store-bff/README.md} 第 4 节与各域 README；本类型只登记形状。</p>
 */
@Data
public class StoreStatsVO {

    /**
     * 上架商品数（快照 · <b>SPU</b> 口径 · {@code shelf_status = 1}）
     */
    private Long onShelfCount;

    /**
     * 下架商品数（快照 · <b>SPU</b> 口径 · {@code shelf_status = 0}）
     */
    private Long offShelfCount;

    /**
     * 库存异常数（快照 · <b>SKU 行</b> 口径）：库存归零、或已跌破 / 触及预警阈值的 SKU 数
     *
     * <p>⚠ 判据是 {@code stock = 0 OR (warn_stock IS NOT NULL AND stock <= warn_stock)}
     * （含相等；未设阈值且库存为 0 也算）。<b>它在域侧是独立的一个 owner 方法</b>，
     * 与库存页「仅看低库存」（只认 {@code stock <= warn_stock}）<b>不是同一条件</b>——
     * 两处名字不同、判据不同，要对齐属另一个需求（见 store README 第 9 节）。</p>
     */
    private Long abnormalStockCount;

    /**
     * 总营业额（窗口 · 按<b>支付时间</b>归属 · 已扣退款）
     */
    private BigDecimal revenue;

    /**
     * 成交订单数量（窗口 · 按支付时间落窗口、按<b>当前状态</b>判定为已完成）
     */
    private Long dealOrderCount;

    /**
     * 成交订单比例 = 成交数 ÷ 窗口内已支付订单数，<b>由本层算</b>（域只回计数）。
     *
     * <p>⚠ <b>可空，且 {@code null} 与 {@code 0} 含义不同</b>：{@code null} = 窗口内<b>没有已支付订单</b>
     * （分母为 0，页面展示「—」），{@code 0} = 有分母但一笔都没成交（真的是 0%）。
     * ⚠ 页面不得把 {@code null} 显示成 0%（那是把「没数据」说成「数据是零」）。</p>
     */
    private BigDecimal dealOrderRatio;

    /**
     * 评价星级分布（窗口 · 按评价行创建时刻落窗口 · <b>固定 1~5 五行升序</b>，无评价的星级占位 0）
     *
     * <p>⚠ 与同层的评价页<b>刻意不一致</b>：那边是「要筛选不要分布」（列表是操作），
     * 这里是「要分布不要筛选」（展示）。域侧 {@code evaluationStat} 两个能力都通用地提供，
     * 本层只取分布。</p>
     */
    private List<StoreGoodsEvaluationScoreCountVO> evaluationScores;

    /**
     * 折线粒度（<b>投放给页面用</b>）
     *
     * <p>⚠ 页面<b>不得</b>自己按窗口天数重算这条规则——那会让「≤180 天按天、否则按月」有第二处实现。
     * 页面拿本字段决定 x 轴标签怎么格式化即可。</p>
     */
    private StatsGrain grain;

    /**
     * 订单数量折线（窗口 · 按<b>下单时刻</b>分桶 · 已按窗口零填充）
     *
     * <p>⚠ 与 {@link #revenue} / {@link #dealOrderCount} 的时间基准<b>刻意不同</b>：
     * 「钱动了才算营业额」按支付时刻，「新下了多少单」按字面的下单时刻。</p>
     *
     * <p>⚠ <b>未开店 / 窗口内无单时是一串 0，不是空列表</b>（零填充在 {@code common} 的
     * {@code StatsSeriesMerger} 里做）——页面照序画线即可，不必自己补桶。</p>
     */
    private List<StatsPointVO> orderSeries;
}
