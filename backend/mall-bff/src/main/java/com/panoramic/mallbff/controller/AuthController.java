package com.panoramic.mallbff.controller;

import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.mallbff.dto.LoginDTO;
import com.panoramic.mallbff.dto.RegisterDTO;
import com.panoramic.mallbff.dto.SmsCodeDTO;
import com.panoramic.mallbff.service.AuthService;
import com.panoramic.mallbff.vo.CurrentUserVO;
import com.panoramic.mallbff.vo.LoginResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端顾客认证接口
 * <p>取码/注册/登录在网关与服务两侧均为白名单，登出与 me 需登录态。
 * <p>⚠ **C 端不接 RBAC**：本类（及本模块全部 controller）没有、也不应有任何 {@code @PreAuthorize}——
 * 顾客登录后对自己的数据全权限，这是预期状态，不是漏登记。</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 获取短信验证码（模拟通道：服务端只打日志，固定码 888888）
     * <p>注册页与登录页共用；必须先于登录调用，故在白名单内。</p>
     */
    @PostMapping("/sms-code")
    public RespData<Void> smsCode(@Valid @RequestBody SmsCodeDTO dto) {
        authService.sendSmsCode(dto);
        return RespData.success();
    }

    /**
     * 注册（注册即登录，签发 type=user 的 JWT）
     */
    @PostMapping("/register")
    public RespData<LoginResultVO> register(@Valid @RequestBody RegisterDTO dto) {
        return RespData.success(authService.register(dto));
    }

    /**
     * 登录：手机号 + 验证码，校验通过后签发 JWT 并缓存顾客登录上下文到 Redis
     */
    @PostMapping("/login")
    public RespData<LoginResultVO> login(@Valid @RequestBody LoginDTO dto) {
        return RespData.success(authService.login(dto));
    }

    /**
     * 登出：删除 Redis 顾客登录上下文（服务端即下线），本地 token 由前端清除
     */
    @PostMapping("/logout")
    public RespData<Void> logout() {
        LoginUser loginUser = UserContext.getLoginUser();
        authService.logout(
                loginUser == null ? LoginUser.USER_TYPE_USER : loginUser.getUserType(),
                UserContext.getUserId());
        return RespData.success();
    }

    /**
     * 当前登录顾客信息（前台顶栏展示；刷新页面时拉取）
     */
    @GetMapping("/me")
    public RespData<CurrentUserVO> me() {
        return RespData.success(authService.currentUser());
    }
}
