package com.panoramic.store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * store 域操作人身份直取过滤器（信任头模式，无 Redis）。
 * <p>紧随 {@link InternalTrustFilter}（验内部令牌）之后执行，对 {@code /internal/**} 请求把端 BFF 透传的
 * {@code X-User-Id} / {@code X-User-Type} 直接填 {@link UserContext} 里的 {@link LoginUser}（id + userType，
 * 不打 Redis）。id 供 MyBatis-Plus 审计字段（create_user/update_user）自动填充；
 * userType（admin/store）供 service 层做「平台/店主」身份分流（D5）。
 * 缺省 {@code X-User-Id} 或 {@code X-User-Type} 说明非可信内部调用，直接 401。
 * ⚠ 不可用 {@code UserContext.set(userId, username)}（userType 缺省会变 admin），须构造带 userType 的
 * {@link LoginUser} 后再 set。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class StoreUserIdentityFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** gateway 透传 userId 的请求头名（与 common LoginUser / gateway AuthGlobalFilter 对齐） */
    private final String userIdHeader;

    public StoreUserIdentityFilter(@Value("${panoramic.auth.header-name:X-User-Id}") String userIdHeader) {
        this.userIdHeader = userIdHeader;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long userId = parseLong(request.getHeader(userIdHeader));
        String userType = request.getHeader(LoginUser.HEADER_USER_TYPE);
        if (userId == null || userType == null || userType.isBlank()) {
            log.warn("内部调用缺少主身份/类型头，拒绝访问: method={}, uri={}",
                    request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        try {
            // id + userType：既满足审计填充，也供 owner/platform 分流；无需 username/perms/roles
            LoginUser loginUser = new LoginUser();
            loginUser.setId(userId);
            loginUser.setUserType(userType.trim());
            UserContext.set(loginUser);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private Long parseLong(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(headerValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("msg", "未携带操作人身份（X-User-Id / X-User-Type）");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), body);
    }
}
