package com.panoramic.goods.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置
 * <p>Spring Boot 4 中 web starter 不再自动注册 ObjectMapper，此处显式提供；
 * 注册 JavaTimeModule 以支持 LocalDateTime 的序列化/反序列化。</p>
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
