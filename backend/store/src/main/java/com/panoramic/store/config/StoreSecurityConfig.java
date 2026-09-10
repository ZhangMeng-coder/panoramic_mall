package com.panoramic.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * store 域内部接口本地安全链（仅匹配 {@code /internal/**}）。
 * <p>store 域权限判定已收敛在端 BFF（admin {@code @PreAuthorize} / store-bff 登录店主），
 * {@code /internal/**} 由 {@link InternalTrustFilter}（验内部令牌）+ {@link StoreUserIdentityFilter}
 * （X-User-Id/X-User-Type 直取填审计与身份分流）把关后放行执行，不再需要 common 的
 * {@code AuthTokenFilter}（每次打 Redis 重建登录用户）。故这里提供一条更高优先级、只匹配内部路径的
 * permitAll 安全链，使 common 的全量认证链对 {@code /internal/**} 不命中、不再打 Redis；非内部路径
 * （如 actuator 健康检查）仍回落 common 链，行为不变。common 代码零改动。</p>
 */
@Configuration
public class StoreSecurityConfig {

    @Bean
    @Order(-100)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 放行全部内部调用：鉴权已在端 BFF 完成，域内信任并执行
                        .anyRequest().permitAll());
        return http.build();
    }
}
