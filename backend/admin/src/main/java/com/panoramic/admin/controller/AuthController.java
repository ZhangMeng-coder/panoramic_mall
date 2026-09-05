package com.panoramic.admin.controller;

import com.panoramic.admin.dto.ChangePasswordDTO;
import com.panoramic.admin.dto.LoginDTO;
import com.panoramic.admin.service.AuthService;
import com.panoramic.admin.service.UserService;
import com.panoramic.admin.vo.CurrentUserVO;
import com.panoramic.admin.vo.LoginResultVO;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认证接口（登录接口在网关与服务两侧均为白名单，其余需登录）
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /**
     * 登录：校验通过后签发 JWT 并缓存登录用户上下文到 Redis
     */
    @PostMapping("/login")
    public RespData<LoginResultVO> login(@Valid @RequestBody LoginDTO dto) {
        return RespData.success(authService.login(dto));
    }

    /**
     * 登出：删除 Redis 登录用户上下文（服务端即下线），本地 token 由前端清除
     */
    @PostMapping("/logout")
    public RespData<Void> logout() {
        authService.logout(UserContext.getUserId());
        return RespData.success();
    }

    /**
     * 当前登录用户信息（含权限，供前端顶栏展示与按钮显隐；刷新页面时拉取）
     */
    @GetMapping("/me")
    public RespData<CurrentUserVO> me() {
        return RespData.success(toCurrentUser(UserContext.getLoginUser()));
    }

    /**
     * 修改当前登录用户密码
     */
    @PutMapping("/password")
    public RespData<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(UserContext.getUserId(), dto.getOldPassword(), dto.getNewPassword());
        return RespData.success();
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
