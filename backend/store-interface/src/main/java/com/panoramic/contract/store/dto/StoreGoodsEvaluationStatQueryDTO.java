package com.panoramic.contract.store.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 评价星级分布查询参数（store 域内部接口与端 BFF 同源共享）。
 * <p><b>跨店通用</b>：与 {@code StoreGoodsEvaluationPageQueryDTO} 同一组可空条件——
 * C 端传 {@code spuId} 看「该商品各星级各有多少人」，商户端传 {@code storeId} 看「本店整体分布」。</p>
 * <p>⚠ 无集合字段，故走 {@code GET + @SpringQueryMap}（不必像分页那样为绕开集合序列化而用 POST）。</p>
 */
@Data
public class StoreGoodsEvaluationStatQueryDTO {

    /**
     * 商品 SPU id；空 = 不限定商品
     */
    private Long spuId;

    /**
     * 作用域：所属店铺 id（= 店主账号 id）；空 = 不限定店铺
     */
    private Long storeId;

    /**
     * 统计区间起点（按评价行 {@code create_time} 筛）；空 = 不限定下界。
     *
     * <p>⚠ <b>这是「窗口」不是「作用域」</b>：它的值来自调用方选的时间段（店主端看板的窗口选择器），
     * 与 {@code storeId} 那种「只能来自登录态」的锚点不是一回事（cross-cutting 第 22 / 25 条）。</p>
     * <p>⚠ <b>必须与 {@link #end} 成对给</b>（只给一端时另一端为「不限定」，语义上是半开区间，
     * 当前无人这么用）；<b>不传 = 全部</b>，C 端商品详情页照旧不传、行为不变。</p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    /**
     * 统计区间终点；空 = 不限定上界。<b>闭区间、含当天整天</b>（域侧取次日 00:00 用 {@code <} 排除）。
     *
     * <p>⚠ 域只收<b>显式日期</b>：窗口枚举（「本月」「上季」）与分桶粒度都不进本接口，
     * 日历算术在发起调用的端 BFF（cross-cutting 第 25 条）。</p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
}
