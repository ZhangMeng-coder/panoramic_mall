package com.panoramic.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS 跨域配置类
 * <p>以 {@link CorsConfigurationSource} Bean 暴露，供 Spring Security 链内 {@code .cors(withDefaults())} 使用，
 * 保证预检请求（OPTIONS）在鉴权前即被放行并携带跨域响应头（引入安全链后不再注册裸 CorsFilter，避免顺序歧义）。</p>
 */
@Configuration
public class CorsConfig {

    /**
     * 跨域配置源（Bean 名 corsConfigurationSource，Security 链默认读取）
     *
     * @return 跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的域名，这里"*"表示允许所有域名跨域访问
        config.addAllowedOriginPattern("*");
        // 允许的请求头
        config.addAllowedHeader("*");
        // 允许的请求方法
        config.addAllowedMethod("*");
        // 允许携带认证信息（如 Authorization 头 / Cookies）
        config.setAllowCredentials(true);
        // 显式暴露自定义响应头（如网关透传/业务透传头需要跨域可见时）
        config.addExposedHeader("Content-Disposition");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
