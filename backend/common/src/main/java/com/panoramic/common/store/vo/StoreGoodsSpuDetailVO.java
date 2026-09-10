package com.panoramic.common.store.vo;

import com.panoramic.common.goods.dto.SpecConfigItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺在售商品详情（store 域出参）。
 * <p>仅含店铺侧字段。中台版本比对结果（centerOutdated / centerSpu / centerMissing）由 store-bff
 * 编排时补充——纯域不调用中台，故不下沉到本类型。</p>
 */
@Data
public class StoreGoodsSpuDetailVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 所属分类ID（引用中台 goods_category）
     */
    private Long categoryId;

    /**
     * 分类名称快照
     */
    private String categoryName;

    /**
     * 品牌ID（引用中台 goods_brand）
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    private String brandName;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 轮播图 URL 列表
     */
    private List<String> imageList;

    /**
     * 商品详情（富文本 HTML）
     */
    private String description;

    /**
     * 规格属性配置
     */
    private List<SpecConfigItem> specConfig;

    /**
     * 上下架：0 下架，1 上架（由 SKU 联动推导）
     */
    private Integer shelfStatus;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 上次关联/同步时中台 SPU 的版本戳（null = 未关联中台）
     */
    private Long centerVersion;

    /**
     * SKU 列表
     */
    private List<StoreGoodsSkuVO> skus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
