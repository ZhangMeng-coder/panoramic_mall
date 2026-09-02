package com.panoramic.goods.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新分类请求参数（仅支持改名/排序，不支持移动父级）
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
}
