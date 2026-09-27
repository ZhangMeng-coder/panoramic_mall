package com.panoramic.contract.store.vo;

import lombok.Data;

/**
 * 本店商品与库存规模（店主端首页数据看板用；store 域内部接口与 store-bff 同源共享）。
 *
 * <p><b>三个计数挤在一个接口里</b>：它们同属「本店商品规模」这一件事，拆三条只会让调用方
 * 多跑两次跨服务往返（与 {@link ShopStatsVO} 合并两个计数同因）。</p>
 *
 * <p>⚠ <b>三个数都是当前累计快照，无窗口</b>：上架 / 下架是「本店现在有多少件在售 / 没在售」，
 * 库存异常是「本店现在有多少个 SKU 该补货」——都不是「本月新增」。别为了和订单统计对称加时间范围。</p>
 *
 * <p>⚠ <b>粒度不同是刻意的</b>：{@link #onShelfCount} / {@link #offShelfCount} 数的是 <b>SPU</b>
 * （{@code store_goods_spu.shelf_status}，由名下 SKU 联动推导），{@link #abnormalStockCount} 数的是
 * <b>SKU 行</b>（{@code stock} / {@code warn_stock} 在 {@code store_goods_sku_stock}）。
 * 三者<b>不可相加、不可比</b>——别拿「上架 + 下架」或「下架 = 库存异常」之类的等式去理解它。</p>
 *
 * <p>⚠ 口径（哪些商品计入、库存异常的判据）属<b>业务规则</b>，钉在本域的三个 owner 方法上，
 * 见 {@code backend/store/README.md} 第 9 节；本类型只登记形状。</p>
 */
@Data
public class StoreGoodsStatsVO {

    /**
     * 上架商品数（<b>SPU</b> 口径：本店未删除且 {@code shelf_status = 1} 的商品；
     * 口径见 store README，等价于「至少一个 SKU 上架」）
     */
    private Long onShelfCount;

    /**
     * 下架商品数（<b>SPU</b> 口径：本店未删除且 {@code shelf_status = 0} 的商品）
     */
    private Long offShelfCount;

    /**
     * 库存异常数（<b>SKU 行</b> 口径：库存归零、或已跌破 / 触及预警阈值的 SKU 数）
     *
     * <p>⚠ 判据是 {@code stock = 0 OR (warn_stock IS NOT NULL AND stock <= warn_stock)}
     * （含相等；未设阈值且库存为 0 也算），<b>与库存页「仅看低库存」不是同一条件</b>——
     * 后者只认 {@code stock <= warn_stock}。两处名字不同、判据不同，要对齐属另一个需求
     * （见 {@code backend/store/README.md}）。</p>
     */
    private Long abnormalStockCount;
}
