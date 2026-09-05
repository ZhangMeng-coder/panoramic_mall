package com.panoramic.store.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册成功响应（token + 当前用户信息）
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
     * 当前用户信息
     */
    private CurrentUserVO user;
}
