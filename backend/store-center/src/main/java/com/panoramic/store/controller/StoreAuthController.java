package com.panoramic.store.controller;

import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.store.dto.LoginDTO;
import com.panoramic.store.dto.RegisterDTO;
import com.panoramic.store.service.AuthService;
import com.panoramic.store.vo.CurrentUserVO;
import com.panoramic.store.vo.LoginResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店主认证接口（注册/登录在网关与服务两侧均为白名单，其余需登录）
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class StoreAuthController {

    private final AuthService authService;

    /**
     * 注册（注册即登录，签发 type=store 的 JWT）
     */
    @PostMapping("/register")
    public RespData<LoginResultVO> register(@Valid @RequestBody RegisterDTO dto) {
        return RespData.success(authService.register(dto));
    }

    /**
     * 登录：校验通过后签发 JWT 并缓存店主登录上下文到 Redis
     */
    @PostMapping("/login")
    public RespData<LoginResultVO> login(@Valid @RequestBody LoginDTO dto) {
        return RespData.success(authService.login(dto));
    }

    /**
     * 登出：删除 Redis 店主登录上下文（服务端即下线），本地 token 由前端清除
     */
    @PostMapping("/logout")
    public RespData<Void> logout() {
        LoginUser loginUser = UserContext.getLoginUser();
        authService.logout(
                loginUser == null ? LoginUser.USER_TYPE_STORE : loginUser.getUserType(),
                UserContext.getUserId());
        return RespData.success();
    }

    /**
     * 当前登录店主信息（店主端顶栏展示；刷新页面时拉取）
     */
    @GetMapping("/me")
    public RespData<CurrentUserVO> me() {
        return RespData.success(authService.currentUser());
    }
}
