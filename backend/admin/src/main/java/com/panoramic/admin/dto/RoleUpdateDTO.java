package com.panoramic.admin.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新角色请求参数
 */
@Data
public class RoleUpdateDTO {

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空", groups = ValidationGroups.Update.class)
    @Size(max = 50, message = "角色名称不能超过50个字符", groups = ValidationGroups.Update.class)
    private String name;

    /**
     * 角色标识（如 admin）
     */
    @NotBlank(message = "角色标识不能为空", groups = ValidationGroups.Update.class)
    @Size(max = 50, message = "角色标识不能超过50个字符", groups = ValidationGroups.Update.class)
    private String code;

    /**
     * 角色描述
     */
    @Size(max = 255, message = "角色描述不能超过255个字符", groups = ValidationGroups.Update.class)
    private String description;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}
