package com.panoramic.common.store.dto;

import com.panoramic.common.goods.dto.SpecConfigItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 店铺在售商品（SPU）修改请求参数（store 域内部接口与 store-bff 同源共享）。
 * <p>与新增的差别：<b>不可改中台关联 id</b>（goodsSpuId 仅在新增时确定）；
 * centerVersion 可空，仅在店主点过「同步」后携带，用于刷新 center_version（未同步则保持原值）。
 * 存在上架 SKU 时规格配置只读，由 store 域校验。</p>
 */
@Data
public class StoreGoodsSpuUpdateDTO {

    /**
     * 商品名称
     */
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称不能超过128个字符")
    private String name;

    /**
     * 所属分类ID（引用中台 goods_category）
     */
    @NotNull(message = "商品分类不能为空")
    private Long categoryId;

    /**
     * 分类名称快照
     */
    @Size(max = 64, message = "分类名称不能超过64个字符")
    private String categoryName;

    /**
     * 品牌ID（引用中台 goods_brand，非必填）
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    @Size(max = 64, message = "品牌名称不能超过64个字符")
    private String brandName;

    /**
     * 主图 URL
     */
    @Size(max = 255, message = "主图地址长度不能超过255个字符")
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
     * 规格属性配置（存在上架 SKU 时不可改，结构校验由服务层完成）
     */
    private List<SpecConfigItem> specConfig;

    /**
     * 中台 SPU 版本戳（可空；点过「同步」后随保存提交，落 center_version）
     */
    private Long centerVersion;
}
