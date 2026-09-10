package com.panoramic.common.auth;

import com.panoramic.common.security.LoginUser;
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
 * 认证过滤器（端 BFF 经 common-auth 启用；业务域不依赖本模块）
 * <p>链路：gateway 已验 JWT + Redis 并把 userId、userType 放进 {@code X-User-Id} / {@code X-User-Type} 头；
 * 本过滤器据此从 Redis 取 LoginUser 重建上下文。无头（直连等）时兜底解析 Bearer JWT。
 * 任一环查不到用户（无头/缓存失效/签名失败），则保持匿名——由 Security 对非白名单接口统一回 401
 * （AuthenticationEntryPoint）。请求结束 finally 清理 UserContext，防止 ThreadLocal 串号。</p>
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
     * 解析当前请求对应的登录用户：优先取网关透传的 userId/userType 头；无头时兜底解析 Bearer JWT。
     *
     * @param request 请求
     * @return 登录用户；解析失败/缓存无此用户返回 null（视为未认证）
     */
    private LoginUser resolveLoginUser(HttpServletRequest request) {
        String userIdHeader = request.getHeader(headerName);
        String userTypeHeader = request.getHeader(LoginUser.HEADER_USER_TYPE);
        String token = bearerToken(request);

        // ① 网关透传头：userId + userType（缺 userType 视为 admin）
        Long headerUserId = parseUserId(userIdHeader);
        if (headerUserId != null) {
            String userType = userTypeHeader != null && !userTypeHeader.isBlank()
                    ? userTypeHeader.trim() : LoginUser.USER_TYPE_ADMIN;
            return loginUserCacheService.get(userType, headerUserId);
        }

        // ② 兜底：Bearer JWT 解析 userId/userType
        if (token != null) {
            Long tokenUserId = jwtService.parseUserId(token);
            if (tokenUserId == null) {
                return null;
            }
            String userType = jwtService.parseUserType(token);
            return loginUserCacheService.get(userType, tokenUserId);
        }
        return null;
    }

    /**
     * 解析 userId 请求头（透传值可能异常，防御式解析）
     */
    private Long parseUserId(String headerUserId) {
        if (headerUserId == null || headerUserId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(headerUserId.trim());
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * 取 Bearer token 串（无/格式不符返回 null）
     */
    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
