package com.panoramic.admin.service;

import com.panoramic.admin.dto.LoginDTO;
import com.panoramic.admin.entity.SysUser;
import com.panoramic.admin.vo.CurrentUserVO;
import com.panoramic.admin.vo.LoginResultVO;
import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.security.JwtService;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.security.LoginUserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;

/**
 * 认证服务（登录编排）
 * <p>跨实体数据一律走各自 owner service：用户（UserService）、用户角色（UserRoleService）、
 * 角色权限（RolePermissionService）、权限（PermissionService）。登录成功后组装 {@link LoginUser}
 * 快照写入 Redis，并签发只含 userId 的 JWT。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserRoleService userRoleService;
    private final RolePermissionService rolePermissionService;
    private final PermissionService permissionService;
    private final LoginUserCacheService loginUserCacheService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 登录：校验用户名/密码/状态 → 组装用户权限快照写入 Redis → 签发 JWT
     *
     * @param dto 登录参数
     * @return token + 当前用户信息
     */
    public LoginResultVO login(LoginDTO dto) {
        SysUser user = userService.getForAuthByUsername(dto.getUsername());
        // 用户不存在 或 密码为空/不匹配 → 统一报“用户名或密码错误”
        if (user == null || !StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ServiceException(ServiceExceptionEnums.USERNAME_PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new ServiceException(ServiceExceptionEnums.USER_DISABLED);
        }

        // 用户 → 角色 → 权限字符串集合
        List<Long> roleIds = userRoleService.roleIdsByUserId(user.getId());
        List<Long> permissionIds = rolePermissionService.permissionIdsByRoleIds(roleIds);
        List<String> perms = permissionService.permsOfIds(permissionIds);

        // 登录用户上下文快照写 Redis（各服务按 userType+userId 查出重建；删除即登出/强制下线）
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType(LoginUser.USER_TYPE_ADMIN);
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setStatus(user.getStatus());
        loginUser.setRoleIds(roleIds);
        loginUser.setPerms(new HashSet<>(perms));
        loginUserCacheService.save(loginUser);

        String token = jwtService.generateToken(user.getId(), LoginUser.USER_TYPE_ADMIN);

        CurrentUserVO currentUser = toCurrentUser(loginUser);
        return new LoginResultVO(token, currentUser);
    }

    /**
     * 登出：删除 Redis 中的登录用户上下文，网关/各服务下一请求即回 401
     *
     * @param userType 用户类型（admin/store）
     * @param userId   用户ID
     */
    public void logout(String userType, Long userId) {
        loginUserCacheService.delete(userType, userId);
    }

    private CurrentUserVO toCurrentUser(LoginUser loginUser) {
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(loginUser.getId());
        vo.setUsername(loginUser.getUsername());
        vo.setNickname(loginUser.getNickname());
        vo.setAvatar(loginUser.getAvatar());
        vo.setPerms(List.copyOf(loginUser.getPerms()));
        return vo;
    }
}
