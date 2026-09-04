package com.panoramic.admin.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建权限请求参数
 */
@Data
public class PermissionSaveDTO {

    /**
     * 父权限ID，0 表示顶级（仅目录）
     */
    @NotNull(message = "父权限ID不能为空", groups = ValidationGroups.Create.class)
    @Min(value = 0, message = "父权限ID不正确", groups = ValidationGroups.Create.class)
    private Long parentId;

    /**
     * 权限名称（菜单/按钮名）
     */
    @NotBlank(message = "权限名称不能为空", groups = ValidationGroups.Create.class)
    @Size(max = 50, message = "权限名称不能超过50个字符", groups = ValidationGroups.Create.class)
    private String name;

    /**
     * 类型：1=目录，2=页面，3=按钮
     */
    @NotNull(message = "权限类型不能为空", groups = ValidationGroups.Create.class)
    @Min(value = 1, message = "权限类型只能为1/2/3", groups = ValidationGroups.Create.class)
    @Max(value = 3, message = "权限类型只能为1/2/3", groups = ValidationGroups.Create.class)
    private Integer type;

    /**
     * 权限字符串，如 system:user:list
     */
    @Size(max = 128, message = "权限字符串不能超过128个字符", groups = ValidationGroups.Create.class)
    private String perms;

    /**
     * 菜单图标
     */
    @Size(max = 128, message = "图标不能超过128个字符", groups = ValidationGroups.Create.class)
    private String icon;

    /**
     * 页面路由地址（type=2 页面必填，如 /user）
     */
    @Size(max = 200, message = "路由地址不能超过200个字符", groups = ValidationGroups.Create.class)
    private String route;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}
