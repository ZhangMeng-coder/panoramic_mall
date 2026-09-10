package com.panoramic.storebff.service;

import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.auth.JwtService;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.auth.LoginUserCacheService;
import com.panoramic.common.util.UserContext;
import com.panoramic.storebff.dto.LoginDTO;
import com.panoramic.storebff.dto.RegisterDTO;
import com.panoramic.storebff.entity.StoreUser;
import com.panoramic.storebff.vo.CurrentUserVO;
import com.panoramic.storebff.vo.LoginResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * 店主认证服务（注册即登录，账号栈归店铺端 BFF）
 * <p>店主独立账号（store_user），userType=store 与平台管理员 admin 经 Redis 键与 JWT type claim 隔离。
 * 店主无 RBAC 角色/权限维度，LoginUser 快照 roleIds/perms 为空集合。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final StoreUserService storeUserService;
    private final LoginUserCacheService loginUserCacheService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 注册（注册即登录）：重名校验 → 建店主账号 → 下发登录态
     *
     * @param dto 注册参数
     * @return token + 当前用户信息
     */
    public LoginResultVO register(RegisterDTO dto) {
        String username = dto.getUsername().trim();
        if (storeUserService.existsByUsername(username)) {
            throw new ServiceException("用户名已存在");
        }
        StoreUser user = new StoreUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname().trim() : username);
        user.setPhone(dto.getPhone());
        user.setStatus(1);
        storeUserService.save(user);
        return issueLogin(user);
    }

    /**
     * 登录：校验用户名/密码/状态 → 写店主登录上下文到 Redis → 签发 type=store 的 JWT
     *
     * @param dto 登录参数
     * @return token + 当前用户信息
     */
    public LoginResultVO login(LoginDTO dto) {
        StoreUser user = storeUserService.getForAuthByUsername(dto.getUsername());
        // 用户不存在 或 密码为空/不匹配 → 统一报“用户名或密码错误”
        if (user == null || !StringUtils.hasText(user.getPassword())
                || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ServiceException(ServiceExceptionEnums.USERNAME_PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new ServiceException(ServiceExceptionEnums.USER_DISABLED);
        }
        return issueLogin(user);
    }

    /**
     * 登出：删除 Redis 中的店主登录上下文，网关/服务下一请求即回 401
     *
     * @param userType 用户类型（store）
     * @param userId   店主账号ID
     */
    public void logout(String userType, Long userId) {
        loginUserCacheService.delete(userType, userId);
    }

    /**
     * 当前登录店主信息（/auth/me，刷新页面时拉取）
     *
     * @return 店主信息
     */
    public CurrentUserVO currentUser() {
        return toCurrentUser(UserContext.getLoginUser());
    }

    /**
     * 签发登录态（登录/注册共用）
     */
    private LoginResultVO issueLogin(StoreUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserType(LoginUser.USER_TYPE_STORE);
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setStatus(user.getStatus());
        loginUser.setRoleIds(Collections.emptyList());
        loginUser.setPerms(new HashSet<>());
        loginUserCacheService.save(loginUser);

        String token = jwtService.generateToken(user.getId(), LoginUser.USER_TYPE_STORE);
        return new LoginResultVO(token, toCurrentUser(loginUser));
    }

    private CurrentUserVO toCurrentUser(LoginUser loginUser) {
        if (loginUser == null) {
            return null;
        }
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(loginUser.getId());
        vo.setUsername(loginUser.getUsername());
        vo.setNickname(loginUser.getNickname());
        vo.setPerms(loginUser.getPerms() == null
                ? List.of() : List.copyOf(loginUser.getPerms()));
        return vo;
    }
}
