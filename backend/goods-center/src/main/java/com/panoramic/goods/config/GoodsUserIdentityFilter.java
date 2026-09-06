package com.panoramic.goods.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * goods-center 操作人身份直取过滤器（信任头模式，无 Redis）。
 * <p>紧随 {@link InternalTrustFilter}（验内部令牌）之后执行，对 {@code /internal/**} 请求把端 BFF 透传的
 * {@code X-User-Id} 直接填入 {@link UserContext}(仅 id)——goods-center 不再按权限需要重建完整登录用户，
 * 也不需要打 Redis。该 id 供 MyBatis-Plus 审计字段（create_user/update_user）自动填充。
 * 缺省 {@code X-User-Id} 说明非 BFF 可信调用（或非用户上下文），直接 401，与旧「必须认证」语义对齐。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GoodsUserIdentityFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** gateway 透传 userId 的请求头名（与 common LoginUser / gateway AuthGlobalFilter 对齐） */
    private final String userIdHeader;

    public GoodsUserIdentityFilter(@Value("${panoramic.auth.header-name:X-User-Id}") String userIdHeader) {
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
        Long userId = parseUserId(request.getHeader(userIdHeader));
        if (userId == null) {
            log.warn("内部调用缺少操作人身份头 {}，拒绝访问: method={}, uri={}",
                    userIdHeader, request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        try {
            // 仅 id 即满足审计填充（MyMetaObjectHandler 只读 getUserId）；无需 username/perms/roles
            UserContext.set(userId, null);
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private Long parseUserId(String headerValue) {
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
        body.put("msg", "未携带操作人身份（X-User-Id）");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), body);
    }
}
