package com.panoramic.goods.config;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * goods-center 操作人身份直取过滤器（信任头模式，无 Redis、不鉴权）。
 * <p>对 {@code /internal/**} 请求把端 BFF 透传的 {@code X-User-Id} / {@code X-User-Type} 直接填
 * {@link UserContext}，供 MyBatis-Plus 审计字段（create_user/update_user）自动填充为
 * {@code UserType:UserId}。</p>
 * <p><b>缺头即不填充、不拦截</b>：域服务不做鉴权（鉴权与权限判定全部收敛在端 BFF），身份头只用于
 * 审计归属；缺头说明调用方未带身份（如定时任务/内部脚本），放行执行、审计字段留空，不再回 401。</p>
 * <p>⚠ 不可用 {@code UserContext.set(userId, username)}（userType 缺省会变 admin），须构造带 userType 的
 * {@link LoginUser} 后再 set。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class GoodsUserIdentityFilter extends OncePerRequestFilter {

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
            // 无身份头：不填充 UserContext，放行（审计字段留空）
            filterChain.doFilter(request, response);
            return;
        }
        String userType = request.getHeader(LoginUser.HEADER_USER_TYPE);
        try {
            LoginUser loginUser = new LoginUser();
            loginUser.setId(userId);
            loginUser.setUserType(userType == null || userType.isBlank() ? null : userType.trim());
            UserContext.set(loginUser);
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
            log.warn("身份头 {} 非法，忽略: value={}", userIdHeader, headerValue);
            return null;
        }
    }
}
