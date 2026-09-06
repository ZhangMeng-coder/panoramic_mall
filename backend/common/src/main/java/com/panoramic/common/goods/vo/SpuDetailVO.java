package com.panoramic.common.goods.vo;

import com.panoramic.common.goods.dto.SpecAttr;
import com.panoramic.common.goods.dto.SpecConfigItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品（SPU）详情响应
 */
@Data
public class SpuDetailVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 品牌名称
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
     * 展示状态：0 隐藏，1 展示
     */
    private Integer status;

    /**
     * 规格属性配置（JSON → List）
     */
    private List<SpecConfigItem> specConfig;

    /**
     * SKU 列表
     */
    private List<SkuVO> skus;

    /**
     * 分类完整链条（根→叶子名称，如 "服饰 / 男装 / T恤"）
     */
    private String categoryPath;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
