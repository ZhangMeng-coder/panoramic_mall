package com.panoramic.common.security;

import com.panoramic.common.util.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 认证过滤器（各业务服务经 common 启用）
 * <p>链路：gateway 已验 JWT + Redis 并把 userId 放进 {@code X-User-Id} 头；
 * 本过滤器据此从 Redis 取 LoginUser 重建上下文。任一环查不到用户（无头/缓存失效/签名失败），
 * 则保持匿名——由 Security 对非白名单接口统一回 401（AuthenticationEntryPoint）。
 * 请求结束 finally 清理 UserContext，防止 ThreadLocal 串号。</p>
 */
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LoginUserCacheService loginUserCacheService;
    private final JwtService jwtService;
    private final String headerName;

    public AuthTokenFilter(LoginUserCacheService loginUserCacheService,
                           JwtService jwtService,
                           String headerName) {
        this.loginUserCacheService = loginUserCacheService;
        this.jwtService = jwtService;
        this.headerName = headerName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            LoginUser loginUser = resolveLoginUser(request);
            if (loginUser != null) {
                List<SimpleGrantedAuthority> authorities = loginUser.getPerms().stream()
                        .filter(perm -> perm != null && !perm.isBlank())
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, authorities);
                authentication.setDetails(loginUser);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                UserContext.set(loginUser);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 解析当前请求对应的登录用户：优先取网关透传的 userId 头；无头时兜底解析 Bearer JWT。
     *
     * @param request 请求
     * @return 登录用户；解析失败/缓存无此用户返回 null（视为未认证）
     */
    private LoginUser resolveLoginUser(HttpServletRequest request) {
        Long userId = resolveUserId(request);
        if (userId == null) {
            return null;
        }
        LoginUser loginUser = loginUserCacheService.get(userId);
        if (loginUser == null) {
            // 查无 Redis 用户上下文：登录态失效（登出/超时/强制下线），按未认证处理
            return null;
        }
        return loginUser;
    }

    private Long resolveUserId(HttpServletRequest request) {
        String headerUserId = request.getHeader(headerName);
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                return Long.valueOf(headerUserId.trim());
            } catch (NumberFormatException ignore) {
                // 头非法则继续尝试 token
            }
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return jwtService.parseUserId(authorization.substring(BEARER_PREFIX.length()).trim());
        }
        return null;
    }
}
