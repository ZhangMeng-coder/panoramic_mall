package com.panoramic.mallbff.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置
 * <p>⚠ 本类在本模块**不是可选项**：Spring Boot 4 中 web starter 不再自动注册 ObjectMapper，
 * 而 common-auth 的 {@code LoginUserCacheService} 要注入一个用于读写 Redis 登录快照——
 * 缺此 bean 则 mall-bff 直接启动失败。注册 JavaTimeModule 以支持 LocalDateTime 的序列化/反序列化。</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Java 8 时间类型支持
        mapper.registerModule(new JavaTimeModule());
        // 时间序列化为 ISO-8601 字符串而非时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
