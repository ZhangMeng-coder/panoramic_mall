package com.panoramic.store.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前登录店主信息（登录返回 & /auth/me 返回；店主端顶栏展示用）
 * <p>店主账号无 RBAC 权限维度，perms 恒为空列表（前端按钮显隐依赖后端能力时后续可扩展）。</p>
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
     * 手机号
     */
    private String phone;

    /**
     * 权限字符串集合（店主账号固定为空）
     */
    private List<String> perms;
}
