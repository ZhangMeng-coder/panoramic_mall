package com.panoramic.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * customer-center 域本地安全链（域服务不鉴权，仅保留一条全放行链）。
 * <p>customer-center 为下沉纯域：鉴权与权限判定全部收敛在端 BFF（本域只有 mall-bff 一个调用方，
 * 其 {@code customerId} 一律从登录态取），本服务只执行领域逻辑 + 由
 * {@link CustomerUserIdentityFilter} 把信任头填进 {@code UserContext} 供审计。
 * 本模块不依赖 common-auth，故不再有 common 的认证链（{@code AuthTokenFilter}/Redis）需要绕开；
 * 这里提供唯一一条全放行链，避免 Spring Security 默认链（formLogin/httpBasic）拦截 actuator 等端点。</p>
 * <p>⚠ 部署前提：8086 端口只在内网可达，否则可伪造 {@code X-User-Id}。</p>
 */
@Configuration
public class CustomerSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 全放行：鉴权已在端 BFF 完成，域内信任并执行
                        .anyRequest().permitAll());
        return http.build();
    }
}
