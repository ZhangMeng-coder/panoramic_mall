package com.panoramic.common.util;

/**
 * 用户上下文工具类
 * <p>基于 ThreadLocal 存储当前请求的用户信息（用户ID、用户名），
 * 可在任何位置调用静态方法获取。请求结束后必须调用 clear() 清理，防止内存泄漏。</p>
 */
public class UserContext {

    /** 当前用户ID */
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    /** 当前用户名 */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    /**
     * 设置当前用户信息
     *
     * @param userId   用户ID
     * @param username 用户名
     */
    public static void set(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    /**
     * 获取当前用户ID
     *
     * @return 用户ID，可能为 null
     */
    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名，可能为 null
     */
    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 清理当前线程的用户信息
     * <p>务必在请求结束时调用，防止 ThreadLocal 内存泄漏。</p>
     */
    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
