package com.panoramic.mallbff.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * C 端商品列表项（页面契约，见 docs/contracts/mall-bff.md）。
 * <p><b>与域出参 {@code StoreGoodsSpuCrossShopPageItemVO} 刻意解耦</b>：域返回的是
 * 「跨店通用」超集（含 {@code lockReason}/{@code lockUser}/{@code lockTime}/
 * {@code goodsSpuId}/{@code categoryPath}/{@code skuCount}/{@code updateTime} 等
 * 管理端或内部字段），**域返回的字段不等于可以对外暴露**——由 {@code CatalogBffService#toMallItem}
 * 逐字段手工映射裁剪出本形状。域 VO 日后加字段不会自动漏到 C 端。</p>
 */
@Data
public class MallGoodsItemVO {

    /**
     * 店铺商品 id
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 主图 URL，空则由前端回退 CSS 渐变占位
     */
    private String mainImage;

    /**
     * 在售 SKU 最低价（「¥xx.xx 起」）
     */
    private BigDecimal minPrice;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 所属店铺名称
     */
    private String storeName;

    /**
     * 所属分类 id
     */
    private Long categoryId;

    /**
     * 分类名称快照（域落库时的叶子分类名）
     */
    private String categoryName;

    /**
     * 品牌 id
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    private String brandName;
}
