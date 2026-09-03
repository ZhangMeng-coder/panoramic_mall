package com.panoramic.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 给角色分配用户请求参数
 */
@Data
public class RoleUserIdsDTO {

    /**
     * 用户ID集合（整体替换；空列表表示清空该角色下所有用户）
     */
    @NotNull(message = "用户ID集合不能为空")
    private List<Long> userIds;
}
