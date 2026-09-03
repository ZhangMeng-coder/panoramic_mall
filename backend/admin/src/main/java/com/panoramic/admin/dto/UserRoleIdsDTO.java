package com.panoramic.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 给用户分配角色请求参数
 */
@Data
public class UserRoleIdsDTO {

    /**
     * 角色ID集合（整体替换；空列表表示清空该用户所有角色）
     */
    @NotNull(message = "角色ID集合不能为空")
    private List<Long> roleIds;
}
