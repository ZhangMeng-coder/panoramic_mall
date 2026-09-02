package com.panoramic.goods.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新商品（SPU）请求参数
 */
@Data
public class SpuUpdateDTO {

    /**
     * 商品名称
     */
    @NotBlank(message = "商品名称不能为空", groups = ValidationGroups.Update.class)
    @Size(max = 128, message = "商品名称不能超过128个字符", groups = ValidationGroups.Update.class)
    private String name;

    /**
     * 所属分类ID（叶子分类）
     */
    @NotNull(message = "商品分类不能为空", groups = ValidationGroups.Update.class)
    private Long categoryId;

    /**
     * 品牌ID
     */
    @NotNull(message = "商品品牌不能为空", groups = ValidationGroups.Update.class)
    private Long brandId;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 轮播图 URL 数组
     */
    private List<String> imageList;

    /**
     * 商品详情（富文本 HTML）
     */
    private String description;

    /**
     * 状态：0 下架，1 上架
     */
    private Integer status;

    /**
     * SKU 列表（diff 更新：带 id 更新，无 id 新增，缺失的存量 SKU 逻辑删除）
     */
    @Valid
    @NotEmpty(message = "商品至少需要一个 SKU", groups = ValidationGroups.Update.class)
    private List<SkuDTO> skus;
}
