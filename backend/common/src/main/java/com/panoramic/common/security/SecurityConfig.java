package com.panoramic.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 安全配置（随 common 被 admin/goods-center 扫描自动启用）
 * <p>无状态 + JWT/Redis 认证：各服务经 gateway 透传的 userId（或兜底 Bearer JWT）由
 * {@link AuthTokenFilter} 从 Redis 重建登录用户；非白名单接口一律要求认证。
 * <ul>
 *   <li>401（未认证/登录态失效）：HTTP 401 + {@code {code:401,msg}}</li>
 *   <li>403（已认证无权限）：沿用现状 body-code（HTTP 200 + {@code {code:403,msg}}），前端按 code 分支提示</li>
 * </ul></p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 默认放行路径（服务侧，路由前缀已剥离），逗号分隔可扩展；登录接口必须放行 */
    @Value("${panoramic.auth.whitelist-paths:/auth/login}")
    private String whitelistPaths;

    /** gateway 透传 userId 的请求头名 */
    @Value("${panoramic.auth.header-name:X-User-Id}")
    private String headerName;

    private final AuthTokenFilter authTokenFilter;

    public SecurityConfig(LoginUserCacheService loginUserCacheService,
                          JwtService jwtService,
                          @Value("${panoramic.auth.header-name:X-User-Id}") String headerName) {
        this.headerName = headerName;
        this.authTokenFilter = new AuthTokenFilter(loginUserCacheService, jwtService, headerName);
    }

    /**
     * 密码加密器（登录校验/新建用户/改密统一注入使用）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        String[] whitelist = whitelistPaths.split("\\s*,\\s*");
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // 未认证 / 登录态失效 → 真实 HTTP 401
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "未登录或登录已失效", objectMapper))
                        // 已认证但权限不足 → 沿用 body-code 403（HTTP 200），与现 GlobalExceptionHandler 契约一致
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_OK, "权限不足", objectMapper)))
                .authorizeHttpRequests(auth -> auth
                        // CORS 预检直接放行（跨域头由 CorsConfigurationSource 处理）
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(whitelist).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 以统一响应结构写回 JSON（{code,msg}，保持与 RespData 同构）
     */
    private void writeError(HttpServletResponse response, int httpStatus, String msg, ObjectMapper objectMapper)
            throws java.io.IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", httpStatus == HttpServletResponse.SC_UNAUTHORIZED ? 401 : 403);
        body.put("msg", msg);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
