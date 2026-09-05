package com.panoramic.common.util;

import com.panoramic.common.security.LoginUser;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 用户上下文工具类
 * <p>基于 ThreadLocal 存储当前请求的登录用户（{@link LoginUser}），可在任何位置调用静态方法获取。
 * 由 common 的安全过滤器在请求进入时写入、请求结束（finally）清理，防止线程复用串号与内存泄漏。
 * 业务代码禁止自行调用 set/clear。</p>
 */
public class UserContext {

    /** 当前登录用户 */
    private static final ThreadLocal<LoginUser> LOGIN_USER = new ThreadLocal<>();

    /**
     * 设置当前登录用户
     *
     * @param loginUser 登录用户上下文
     */
    public static void set(LoginUser loginUser) {
        LOGIN_USER.set(loginUser);
    }

    /**
     * 仅设 userId/username 的便捷入口（无角色/权限；供确不需要完整上下文的最小场景使用）
     *
     * @param userId   用户ID
     * @param username 用户名
     */
    public static void set(Long userId, String username) {
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setUsername(username);
        LOGIN_USER.set(user);
    }

    /**
     * 获取当前登录用户
     *
     * @return 登录用户，未登录为 null
     */
    public static LoginUser getLoginUser() {
        return LOGIN_USER.get();
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，可能为 null（未登录）
     */
    public static Long getUserId() {
        LoginUser user = LOGIN_USER.get();
        return user == null ? null : user.getId();
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名，可能为 null
     */
    public static String getUsername() {
        LoginUser user = LOGIN_USER.get();
        return user == null ? null : user.getUsername();
    }

    /**
     * 获取当前用户的角色 ID 集合
     *
     * @return 角色 ID 列表（未登录或未带角色为空集合）
     */
    public static List<Long> getRoleIds() {
        LoginUser user = LOGIN_USER.get();
        return user == null ? Collections.emptyList() : user.getRoleIds();
    }

    /**
     * 获取当前用户的权限字符串集合
     *
     * @return perms 集合（未登录为空集合）
     */
    public static Set<String> getPerms() {
        LoginUser user = LOGIN_USER.get();
        return user == null ? Collections.emptySet() : user.getPerms();
    }

    /**
     * 判断当前用户是否拥有指定权限
     *
     * @param perm 权限字符串，如 system:user:add
     * @return true=拥有；未登录恒为 false
     */
    public static boolean hasPerm(String perm) {
        LoginUser user = LOGIN_USER.get();
        return user != null && user.hasPerm(perm);
    }

    /**
     * 清理当前线程的用户信息
     * <p>由安全过滤器在请求结束时调用，防止 ThreadLocal 内存泄漏与线程复用串号。</p>
     */
    public static void clear() {
        LOGIN_USER.remove();
    }
}
