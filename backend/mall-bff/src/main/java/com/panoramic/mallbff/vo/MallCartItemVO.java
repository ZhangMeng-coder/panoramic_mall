package com.panoramic.mallbff.vo;

import com.panoramic.contract.store.dto.SpecAttr;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车行响应（<b>本端私有类型</b>，{@code GET /cart} 里每个店铺分组的元素）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.vo.TradeCartItemVO} <b>不是一份东西</b>：
 * 域只出「这一行是什么」（id / spuId / skuId / 数量 / 选中），商品名、图、规格、价格、库存
 * 都由本端在<b>读时</b>经 store 域批量详情补上并判定可见性——域不持商品信息，也不判 C 端可见性。</p>
 *
 * <p><b>两个状态字段分工不同，别合并</b>（合并后「已下架」与「已到库存上限」在页面上同形，提示语写不对）：</p>
 * <ul>
 *   <li>{@link #invalid} —— <b>商品本身不可买</b>：店铺未审核 / SPU 已下架 / SPU 被平台锁定 / SPU 已删 /
 *       该 SKU 已下架。这是 C 端商品可见性不变量（docs/contracts/cross-cutting.md 第 20 条）的
 *       <b>第三个落点</b>。失效行<b>不从列表里删掉</b>（顾客得看得见才敢删），但不计入件数与金额。</li>
 *   <li>{@link #purchasable} —— 商品可见，但<b>这一行不能再加</b>（{@code invalid} 或数量已达可用库存）
 *       → 页面禁「+」。</li>
 * </ul>
 *
 * <p>失效行的字段可得性分两档（页面据此写不同文案，别用「名称是否为空」猜原因）：
 * SPU <b>还在</b>（下架 / 锁定 / 店铺未过审 / SKU 下架）→ 名称、图、店铺归属都在，页面可照常显示商品；
 * SPU <b>已被物理删除</b> → 名称、图、价格、规格全为 {@code null}（连店铺归属都没有，只能归进兜底分组）。</p>
 */
@Data
public class MallCartItemVO {

    /**
     * 购物车行 id（改数量 / 改选中 / 删除都用它定位）
     */
    private Long id;

    /**
     * 店铺商品 SPU id（点回详情页用；⚠ SPU 已删时该详情页会 404，与 {@link #invalid} 同因）
     */
    private Long spuId;

    /**
     * 店铺商品 SKU id
     */
    private Long skuId;

    /**
     * 商品名（SPU 名）；<b>{@code null} = 该 SPU 已被删除</b>，页面显示「商品已删除」占位
     */
    private String name;

    /**
     * 商品图 URL（SPU 主图）；空则由前端回退 CSS 渐变占位
     */
    private String mainImage;

    /**
     * 规格属性组合（如 [{spec:"颜色",value:"曜石黑"}]）；页面拼成「颜色：曜石黑 / 容量：256G」。
     * <p>⚠ 顺序按库里 {@code spec_attrs} 的原样顺序（购物车行是逐 SKU 的，没有详情页那份
     * {@code specConfig} 可用来重排维度），故同一商品的不同行维度顺序可能不一致——纯展示问题，不重排。</p>
     */
    private List<SpecAttr> specAttrs;

    /**
     * SKU 单价；{@code null} = 拿不到商品信息（SPU 已删）
     */
    private BigDecimal price;

    /**
     * 可用库存（= 域侧 {@code availableStock} = {@code stock − locked_stock}）；拿不到商品信息时为 0
     */
    private Integer availableStock;

    /**
     * 购买数量（1..999）
     */
    private Integer quantity;

    /**
     * 选中态（<b>服务端持久化</b>）。⚠ 失效行的选中位同样落库（域侧「全选」是整表操作），
     * 但页面不勾它、也不让它进合计——判断依据是 {@link #invalid}，不是这个字段。
     */
    private Boolean selected;

    /**
     * 是否已失效（商品不可买）——见类注释；失效行不计入 {@code totalQuantity} / {@code selectedQuantity} / {@code selectedAmount}
     */
    private Boolean invalid;

    /**
     * 这一行是否还能再加数量（{@code !invalid && quantity < availableStock}）→ 页面「+」的可用性
     */
    private Boolean purchasable;
}
