package com.panoramic.goods.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
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
     * 展示状态：0 隐藏，1 展示
     */
    private Integer status;

    /**
     * 规格属性配置（可选，空 = 无规格的商品）。形如 [{"spec":"颜色","values":["黑色","白色"]}]
     * 结构校验由服务层完成；变更不得使存量 SKU 引用的规格/属性值失效
     */
    private List<SpecConfigItem> specConfig;
}
