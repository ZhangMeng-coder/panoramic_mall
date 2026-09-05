package com.panoramic.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功响应（token + 当前用户信息）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResultVO {

    /**
     * 访问令牌（后续请求放 Authorization: Bearer <token>）
     */
    private String token;

    /**
     * 当前用户信息（含权限，前端缓存用于按钮显隐）
     */
    private CurrentUserVO user;
}
