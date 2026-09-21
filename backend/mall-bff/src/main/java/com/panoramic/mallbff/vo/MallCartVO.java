package com.panoramic.mallbff.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车响应（<b>本端私有类型</b>，{@code GET /cart} 的出参）。
 *
 * <p>一次编排 = trade-center 取行 + store 域<b>批量详情一次调用</b> + 可见性判定 + 按店铺分组汇总；
 * 逐行调详情是 N+1，禁止（见 docs/contracts/mall-bff.md「购物车读口径」）。</p>
 *
 * <p><b>汇总三件套的口径：只算「有效」</b>——失效行（下架 / 锁定 / 店铺未过审 / 已删）不进
 * {@link #totalQuantity} 与 {@link #selectedQuantity}，更不进 {@link #selectedAmount}
 * （金额只算真能下单的行）；失效行数单独走 {@link #invalidCount}，页面照常把它们显示出来。</p>
 *
 * <p>⚠ 与顶栏徽标的 {@code GET /cart/count} <b>口径不同、不是 bug</b>：徽标是<b>行数</b>
 * （轻口径：域侧 {@code count(*)} + Redis 读穿透，不做可见性判定，故每页都能廉价刷新）；
 * 这里是<b>有效行的件数之和</b>。徽标数「车里有几项」，这里算「能买几件、多少钱」。</p>
 */
@Data
public class MallCartVO {

    /**
     * 按店铺分组的购物车行（不可空；空购物车时为空列表）
     */
    private List<MallCartShopVO> shops;

    /**
     * 有效行的<b>件数</b>之和（quantity 累加，不是行数）
     */
    private Integer totalQuantity;

    /**
     * 选中且有效行的件数之和
     */
    private Integer selectedQuantity;

    /**
     * 选中且有效行的金额之和（{@code price × quantity} 累加，两位小数 HALF_UP）；
     * <b>本端算好下发</b>，前端不要自己再乘加一遍（两份就会漂）
     */
    private BigDecimal selectedAmount;

    /**
     * 失效行数（页面用它决定是否显示「N 件商品已失效」的提示条）
     */
    private Integer invalidCount;
}
