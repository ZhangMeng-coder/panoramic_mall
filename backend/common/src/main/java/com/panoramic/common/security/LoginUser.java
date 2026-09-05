package com.panoramic.common.security;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 登录用户上下文（登录成功后写入 Redis，各业务服务按 user_id 查出后填充 {@code UserContext} 与方法安全主体）
 * <p>仅存需要跨服务携带的快照字段：身份、角色 ID、权限字符串集合。
 * 权限为登录时快照——改动角色/权限后需重新登录刷新（Redis 缓存 + JWT）。</p>
 */
@Data
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（JWT 的 subject，也是 Redis 键与跨服务传递的 X-User-Id）
     */
    private Long id;

    /**
     * 用户名（登录账号）
     */
    private String username;

    /**
     * 昵称/姓名（顶栏展示用）
     */
    private String nickname;

    /**
     * 头像 URL
     */
    private String avatar;

    /**
     * 状态：1 启用，0 停用（登录时校验，Redis 快照仅供参考）
     */
    private Integer status;

    /**
     * 该用户拥有的角色 ID（菜单按角色过滤用）
     */
    private List<Long> roleIds;

    /**
     * 该用户拥有的权限字符串集合（含页面/按钮级 perms），方法鉴权 hasAuthority 的比对来源
     */
    private Set<String> perms;

    public List<Long> getRoleIds() {
        return roleIds == null ? Collections.emptyList() : roleIds;
    }

    public Set<String> getPerms() {
        return perms == null ? Collections.emptySet() : perms;
    }

    /**
     * 判断是否拥有某权限字符串
     *
     * @param perm 权限字符串，如 system:user:add
     * @return true=拥有
     */
    public boolean hasPerm(String perm) {
        return perm != null && getPerms().contains(perm);
    }
}
