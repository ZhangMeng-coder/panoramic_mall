package com.panoramic.common.goods.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建品牌请求参数
 */
@Data
public class BrandSaveDTO {

    /**
     * 品牌名称
     */
    @NotBlank(message = "品牌名称不能为空", groups = ValidationGroups.Create.class)
    @Size(max = 64, message = "品牌名称不能超过64个字符", groups = ValidationGroups.Create.class)
    private String name;

    /**
     * 品牌 LOGO URL
     */
    private String logo;

    /**
     * 品牌简介
     */
    @Size(max = 500, message = "品牌简介不能超过500个字符", groups = ValidationGroups.Create.class)
    private String description;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}
