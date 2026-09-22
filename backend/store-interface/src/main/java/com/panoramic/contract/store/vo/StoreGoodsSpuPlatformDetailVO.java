package com.panoramic.contract.store.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺在售商品详情 · 域出参（商品详情能力的<b>唯一</b>出参类型，跨店通用）
 * <p>继承 {@link StoreGoodsSpuDetailVO}（基础信息 / 图片 / 富文本 / 规格配置 / SKU 列表 / 锁定字段），
 * 追加两项：所属店铺名与分类全路径。作用域不体现在类型上——店主侧经入参 {@code storeId} 限定本店、
 * 管理端与 C 端不限定（跨店），差别只在调用方传入的作用域，域内不判身份、不分侧。</p>
 * <p>字段来源：{@code storeName} 由 store 域填充；{@code categoryPath} 由端 BFF 读时解析（域不持分类表）。</p>
 * <p>⚠ <b>这是管理端超集</b>（含 {@code lockUser} / {@code goodsSpuId} 等）：域返回的字段不等于可以对外暴露，
 * 各端 BFF 输出前自行裁剪（商户端不展示锁定人、C 端整块裁剪），见 docs/contracts/store.md 第三节。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsSpuPlatformDetailVO extends StoreGoodsSpuDetailVO {

    /**
     * 所属店铺名称（store 域填充，取 store_shop.shop_name）
     */
    private String storeName;

    /**
     * 分类全路径（如「服饰 / 男装 / T恤」），由 admin BFF 读时解析；解析失败为空
     */
    private String categoryPath;
}
