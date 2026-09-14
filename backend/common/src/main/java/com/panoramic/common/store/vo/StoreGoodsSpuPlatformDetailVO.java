package com.panoramic.common.store.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺在售商品详情 · platform 侧（管理后台只读详情页）
 * <p>继承 owner 侧 {@link StoreGoodsSpuDetailVO}（含基础信息 / 图片 / 富文本 / 规格配置 / SKU 列表 /
 * 锁定字段），只追加平台侧独有两项：所属店铺名与分类全路径。
 * 与既有 {@code StoreGoodsSpuDetailBffVO extends StoreGoodsSpuDetailVO} 同款「子类扩字段」做法，
 * 避免为平台多出的两个字段污染 owner 侧出参。</p>
 * <p>字段来源：{@code storeName} 由 store 域填充；{@code categoryPath} 由 admin BFF 读时解析（域不持分类表）。</p>
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
