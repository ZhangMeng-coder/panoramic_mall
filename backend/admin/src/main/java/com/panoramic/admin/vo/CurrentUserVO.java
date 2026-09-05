package com.panoramic.admin.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（登录返回 & /auth/me 返回；前端顶栏展示与按钮权限判断用）
 */
@Data
public class CurrentUserVO {

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
     * 头像 URL
     */
    private String avatar;

    /**
     * 权限字符串集合（前端 v-perm 按钮显隐比对用）
     */
    private List<String> perms;
}
