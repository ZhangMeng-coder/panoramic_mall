package com.panoramic.admin.vo;

import com.panoramic.common.stats.StatsGrain;
import com.panoramic.common.stats.StatsPointVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理后台首页看板（**8 个指标挤在一个接口里**，见 {@code docs/contracts/admin.md}）。
 *
 * <p>首页一次加载要么全有要么全无，拆成 4 个接口只会带来 4 次往返、4 份 loading 态。</p>
 *
 * <p>⚠ <b>指标的时间基准分两类，不是笔误</b>：
 * {@link #userCount} / {@link #shopCount} / {@link #goodsCount} 是**当前累计快照**
 * （不受窗口影响、也不受入参影响）；其余 5 个按窗口算。</p>
 *
 * <p>⚠ 前一类的口径是**近似**：{@link #userCount} 数的是**顾客资料行**，
 * **不等于**注册用户数（偏差两个方向都有，见 {@code docs/contracts/customer-center.md}），
 * 对外文案**不得**写成「注册用户数」。</p>
 *
 * <p>⚠ 折线点与粒度用的是 {@code common} 的共用类型（{@link StatsPointVO} / {@link StatsGrain}），
 * 与店主端看板同一份——它们是「BFF 补零归并之后的点」，与域侧那几个 {@code *StatsPointVO}
 * **不可互相替换**。</p>
 */
@Data
public class AdminStatsVO {

    /**
     * 用户数量（快照 · 顾客资料行计数 · **近似**，见类注释）
     */
    private Long userCount;

    /**
     * 商家数量（快照 · 审核已通过的店铺数）
     */
    private Long shopCount;

    /**
     * 商家商品数量（快照 · 未删除的全部 SPU）
     *
     * <p>⚠ 它与 {@link #shopCount} **互不对齐是正常的**：商品数不按上架状态、不按店铺状态、
     * 不按平台锁定筛，而商家数只数审核通过的店。</p>
     */
    private Long goodsCount;

    /**
     * 总营业额（窗口 · 按**支付时间**归属 · 已扣退款）
     */
    private BigDecimal revenue;

    /**
     * 成交订单数量（窗口 · 按支付时间落窗口、按**当前状态**判定为已完成）
     */
    private Long dealOrderCount;

    /**
     * 成交订单比例 = 成交数 ÷ 窗口内已支付订单数，**由本层算**（域只回计数）。
     *
     * <p>⚠ <b>可空，且 {@code null} 与 {@code 0} 含义不同</b>：{@code null} = 窗口内**没有已支付订单**
     * （分母为 0，页面展示「—」），{@code 0} = 有分母但一笔都没成交（真的是 0%）。
     * ⚠ 页面不得把 {@code null} 显示成 0%（那是把「没数据」说成「数据是零」）。</p>
     */
    private BigDecimal dealOrderRatio;

    /**
     * 折线粒度（**投放给页面用**，见下）
     *
     * <p>⚠ 页面**不得**自己按窗口天数重算这条规则——那会让「≤180 天按天、否则按月」有第二处实现。
     * 页面拿本字段决定 x 轴标签怎么格式化即可。</p>
     */
    private StatsGrain grain;

    /**
     * 新增用户折线（窗口 · 按顾客资料行的创建时刻分桶 · 已按窗口零填充）
     */
    private List<StatsPointVO> userSeries;

    /**
     * 新增订单折线（窗口 · 按**下单时刻**分桶 · 已按窗口零填充）
     *
     * <p>⚠ 与 {@link #revenue} / {@link #dealOrderCount} 的时间基准**刻意不同**：
     * 「钱动了才算营业额」按支付时刻，「新下了多少单」按字面的下单时刻。</p>
     */
    private List<StatsPointVO> orderSeries;
}
