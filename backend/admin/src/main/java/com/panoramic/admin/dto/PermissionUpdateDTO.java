package com.panoramic.admin.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新权限请求参数
 * <p>仅允许更新名称/权限字符串/图标/路由地址/排序；父级与类型不可变更（树形约束）。</p>
 */
@Data
public class PermissionUpdateDTO {

    /**
     * 权限名称（菜单/按钮名）
     */
    @NotBlank(message = "权限名称不能为空", groups = ValidationGroups.Update.class)
    @Size(max = 50, message = "权限名称不能超过50个字符", groups = ValidationGroups.Update.class)
    private String name;

    /**
     * 权限字符串，如 system:user:list
     */
    @Size(max = 128, message = "权限字符串不能超过128个字符", groups = ValidationGroups.Update.class)
    private String perms;

    /**
     * 菜单图标
     */
    @Size(max = 128, message = "图标不能超过128个字符", groups = ValidationGroups.Update.class)
    private String icon;

    /**
     * 页面路由地址（type=2 页面必填，如 /user）
     */
    @Size(max = 200, message = "路由地址不能超过200个字符", groups = ValidationGroups.Update.class)
    private String route;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}
