package com.panoramic.mallbff.service;

import com.panoramic.common.auth.JwtService;
import com.panoramic.common.auth.LoginUserCacheService;
import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.mallbff.dto.LoginDTO;
import com.panoramic.mallbff.dto.RegisterDTO;
import com.panoramic.mallbff.dto.SmsCodeDTO;
import com.panoramic.mallbff.entity.MallUser;
import com.panoramic.mallbff.vo.CurrentUserVO;
import com.panoramic.mallbff.vo.LoginResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * C 端顾客认证服务（手机号 + 短信验证码，注册即登录；账号栈归商城前台 BFF）
 * <p>顾客独立账号（mall_user），userType=user 与平台管理员 admin、店主 store 经 Redis 键与
 * JWT type claim 隔离。C 端无 RBAC 角色/权限维度，LoginUser 快照 roleIds/perms 为空集合。</p>
 * <p>⚠ **短信通道为模拟实现**：取码只打日志、不发真实短信、不落库、不落 Redis；校验一律与
 * {@code panoramic.mall.sms-fixed-code}（默认 888888）比对。接真实短信服务时，只需替换
 * {@link #sendSmsCode} 与 {@link #assertCode} 两处。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MallUserService mallUserService;
    private final LoginUserCacheService loginUserCacheService;
    private final JwtService jwtService;

    /**
     * 模拟短信的固定验证码（Nacos/本地配置；缺省 888888）
     */
    @Value("${panoramic.mall.sms-fixed-code:888888}")
    private String smsFixedCode;

    /**
     * 「获取验证码」：只做格式校验与日志留痕，**不真发短信、不落库、不落 Redis**
     * <p>手机号格式由 {@link SmsCodeDTO} 的 {@code @Pattern} 在前置校验完成，此处不再重复判断；
     * 也不校验手机号是否已注册——注册页与登录页共用本接口，两侧都要求能取到码。</p>
     *
     * @param dto 手机号
     */
    public void sendSmsCode(SmsCodeDTO dto) {
        log.info("[模拟短信] 手机号 {} 的验证码为:{}", dto.getPhone(), smsFixedCode);
    }

    /**
     * 注册（注册即登录）：验码 → 手机号查重 → 建账号 → 下发登录态
     *
     * @param dto 注册参数
     * @return token + 当前用户信息
     */
    public LoginResultVO register(RegisterDTO dto) {
        String phone = dto.getPhone().trim();
        assertCode(dto.getCode());
        if (mallUserService.existsByPhone(phone)) {
            throw new ServiceException("手机号已注册");
        }
        MallUser user = new MallUser();
        user.setPhone(phone);
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname().trim() : phone);
        user.setStatus(1);
        mallUserService.save(user);
        return issueLogin(user);
    }

    /**
     * 登录：验码 → 按手机号查账号 → 校验状态 → 写顾客登录上下文到 Redis → 签发 type=user 的 JWT
     *
     * @param dto 登录参数
     * @return token + 当前用户信息
     */
    public LoginResultVO login(LoginDTO dto) {
        String phone = dto.getPhone().trim();
        assertCode(dto.getCode());
        MallUser user = mallUserService.getByPhone(phone);
        if (user == null) {
            throw new ServiceException("手机号未注册");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new ServiceException(ServiceExceptionEnums.USER_DISABLED);
        }
        return issueLogin(user);
    }

    /**
     * 登出：删除 Redis 中的顾客登录上下文，网关/服务下一请求即回 401
     *
     * @param userType 用户类型（user）
     * @param userId   顾客账号ID
     */
    public void logout(String userType, Long userId) {
        loginUserCacheService.delete(userType, userId);
    }

    /**
     * 当前登录顾客信息（/auth/me，刷新页面时拉取）
     *
     * @return 顾客信息
     */
    public CurrentUserVO currentUser() {
        return toCurrentUser(UserContext.getLoginUser());
    }

    /**
     * 校验短信验证码（模拟通道：与固定码比对）
     *
     * @param code 用户提交的验证码
     */
    private void assertCode(String code) {
        if (!StringUtils.hasText(code) || !smsFixedCode.equals(code.trim())) {
            throw new ServiceException("验证码错误");
        }
    }

    /**
     * 签发登录态（登录/注册共用）
     */
    private LoginResultVO issueLogin(MallUser user) {
        LoginUser loginUser = new LoginUser();
        // ⚠ 本行与下一处的 generateToken 必须成对一致（都是 USER_TYPE_USER）：
        //   网关按 JWT type claim 拼 Redis 键 panoramic:login:user:{id} 校验登录态，
        //   两处不一致 → 网关查不到会话 → 该端全部 401。
        loginUser.setUserType(LoginUser.USER_TYPE_USER);
        loginUser.setId(user.getId());
        // 账号即手机号：手机号同时作为快照的 username（LoginUser 无独立 phone 字段，手机号由本字段承载）
        loginUser.setUsername(user.getPhone());
        loginUser.setNickname(user.getNickname());
        loginUser.setStatus(user.getStatus());
        loginUser.setRoleIds(Collections.emptyList());
        loginUser.setPerms(new HashSet<>());
        loginUserCacheService.save(loginUser);

        String token = jwtService.generateToken(user.getId(), LoginUser.USER_TYPE_USER);
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
        // 账号即手机号：phone 直接取快照的 username，**不查库**（LoginUser 无独立 phone 字段）。
        // ⚠ 这是与 store-bff 的行为差异点：store-bff 从不填 phone，别"照着"把它删掉。
        vo.setPhone(loginUser.getUsername());
        vo.setPerms(loginUser.getPerms() == null
                ? List.of() : List.copyOf(loginUser.getPerms()));
        return vo;
    }
}
