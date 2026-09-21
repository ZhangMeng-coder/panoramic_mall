package com.panoramic.mallbff.vo;

import lombok.Data;

import java.util.List;

/**
 * 购物车按店铺分组（<b>本端私有类型</b>，{@code GET /cart} 的出参元素）。
 *
 * <p>分组是在本端做的聚合：购物车行只存 {@code spuId}，店铺归属要经 store 域批量详情
 * 取回 {@code storeId} / {@code storeName} 才知道（域不提供「按店铺分组」这种页面形状）。</p>
 *
 * <p>⚠ <b>{@code shopId} 为 {@code null} 是「兜底分组」</b>：该 SPU 已被删除，连店铺归属都拿不到，
 * 只能归到一组里（{@link #shopName} 给固定文案）。页面照常渲染 {@code shopName} 即可，
 * 但不要用 {@code shopId} 做任何跳转 / 筛选——它是空的。</p>
 */
@Data
public class MallCartShopVO {

    /**
     * 店铺 id（= 店主账号 id）；<b>{@code null} = 兜底分组（商品已删，拿不到店铺归属）</b>
     */
    private Long shopId;

    /**
     * 店铺名；兜底分组时为固定文案（如「已失效商品」）
     */
    private String shopName;

    /**
     * 该店铺下的购物车行（不可空）
     */
    private List<MallCartItemVO> items;
}
