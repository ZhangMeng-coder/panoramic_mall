package com.panoramic.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 给角色分配权限请求参数
 */
@Data
public class RolePermissionIdsDTO {

    /**
     * 权限ID集合（整体替换；空列表表示清空该角色所有权限）
     */
    @NotNull(message = "权限ID集合不能为空")
    private List<Long> permissionIds;
}
