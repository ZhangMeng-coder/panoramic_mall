package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺在售商品（SPU）实体。
 * <p>字段与中台标准商品同构，但归属店铺（{@code store_id}，= 店主账号 id，账号店同 ID），
 * 且带「中台关联」（{@code goods_spu_id} + {@code center_version} 版本戳快照）与上下架状态。
 * 分类/品牌以「id 引用 + 名称快照」落库（下拉数据来自中台，保存时不再回查中台）。</p>
 * <p>{@code shelf_status} 不独立可改：它由名下 SKU 联动推导，
 * 不变量为 {@code SPU上架 ⟺ ≥1 个 SKU 上架}（见 {@code StoreGoodsSpuServiceImpl} 的联动刷新）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_spu")
public class StoreGoodsSpu extends BaseEntity {

    /** 上下架：下架 */
    public static final int SHELF_OFF = 0;
    /** 上下架：上架 */
    public static final int SHELF_ON = 1;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 上次关联/同步时中台 SPU 的版本戳（null = 未关联中台）
     */
    private Long centerVersion;

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
     * 品牌ID（引用中台 goods_brand，可空）
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
     * 轮播图 URL JSON 数组字符串（实体为 String，VO 层转 List）
     */
    private String imageList;

    /**
     * 商品详情（富文本 HTML）
     */
    private String description;

    /**
     * 规格属性配置 JSON 字符串（实体为 String，VO 层转 List）
     */
    private String specConfig;

    /**
     * 上下架：0 下架，1 上架（由 SKU 联动推导，不接受前端直接传入）
     */
    private Integer shelfStatus;
}
