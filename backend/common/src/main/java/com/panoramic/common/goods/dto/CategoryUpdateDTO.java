package com.panoramic.common.goods.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新分类请求参数（仅支持改名/排序/改图标，不支持移动父级）
 */
@Data
public class CategoryUpdateDTO {

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空", groups = ValidationGroups.Update.class)
    private String name;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 分类图标图片 URL（可空）。仅做非空时的长度校验，不校验可达性。
     */
    @Size(max = 255, message = "图标 URL 不能超过 255 个字符", groups = ValidationGroups.Update.class)
    private String icon;
}
