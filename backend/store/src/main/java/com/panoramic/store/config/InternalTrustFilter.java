package com.panoramic.store.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.feign.InternalHeaders;
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
 * store 域内部调用令牌校验过滤器。
 * <p>对 {@code /internal/**} 请求要求携带内部信任头 {@code X-Internal-Token} 且与本地配置的
 * {@code panoramic.internal.secret} 一致——证明调用方是可信的端 BFF（走注册中心内部 Feign），
 * 而非可直接触达本服务端口的页面/公网流量。权限判定已收敛在端 BFF，本服务只负责执行：
 * 令牌校验通过后由 {@link StoreUserIdentityFilter} 直取 X-User-Id/X-User-Type 填 UserContext，
 * 再由 {@link StoreSecurityConfig} 本地安全链放行。本过滤器以最高优先级先于安全链执行。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalTrustFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String internalSecret;

    public InternalTrustFilter(@Value("${panoramic.internal.secret:panoramic-mall-internal-dev-token}") String internalSecret) {
        this.internalSecret = internalSecret;
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
        String token = request.getHeader(InternalHeaders.TRUST_TOKEN);
        if (internalSecret == null || !internalSecret.equals(token)) {
            log.warn("内部调用令牌缺失或不匹配，拒绝访问: method={}, uri={}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("msg", "内部调用令牌无效");
        OBJECT_MAPPER.writeValue(response.getOutputStream(), body);
    }
}
