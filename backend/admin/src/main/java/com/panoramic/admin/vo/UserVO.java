package com.panoramic.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应
 * <p>不包含密码字段，避免泄露。</p>
 */
@Data
public class UserVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户名（登录账号）
     */
    private String username;

    /**
     * 昵称/姓名
     */
    private String nickname;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 状态：1 启用，0 停用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
