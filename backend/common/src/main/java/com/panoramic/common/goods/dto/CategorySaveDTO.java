package com.panoramic.common.goods.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建分类请求参数
 */
@Data
public class CategorySaveDTO {

    /**
     * 父分类ID，0 表示顶级
     */
    @NotNull(message = "父分类ID不能为空", groups = ValidationGroups.Create.class)
    private Long parentId;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空", groups = ValidationGroups.Create.class)
    private String name;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 分类图标图片 URL（可空）。仅做非空时的长度校验，不校验可达性。
     */
    @Size(max = 255, message = "图标 URL 不能超过 255 个字符", groups = ValidationGroups.Create.class)
    private String icon;
}
