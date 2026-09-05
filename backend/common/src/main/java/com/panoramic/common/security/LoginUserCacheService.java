package com.panoramic.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录用户上下文缓存（Redis）
 * <p>key = {@code redis-prefix + userId}，value 为 {@link LoginUser} 的 JSON 快照，TTL 与 token 有效期对齐。
 * 删除 key 即强制下线/登出（gateway 与各业务服务下一请求因查无此键而回 401）。</p>
 */
@Slf4j
@Service
public class LoginUserCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final long expireSeconds;

    public LoginUserCacheService(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${panoramic.auth.redis-prefix:panoramic:login:user}") String keyPrefix,
                                 @Value("${panoramic.auth.jwt-expire-seconds:7200}") long expireSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.expireSeconds = expireSeconds;
    }

    /**
     * 保存登录用户（写入即刷新 TTL）
     *
     * @param loginUser 登录用户
     */
    public void save(LoginUser loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(loginUser.getId()),
                    objectMapper.writeValueAsString(loginUser),
                    Duration.ofSeconds(expireSeconds));
        } catch (Exception e) {
            log.error("写入登录用户缓存失败, userId={}", loginUser.getId(), e);
            throw new IllegalStateException("登录状态写入失败，请稍后重试", e);
        }
    }

    /**
     * 按 userId 读取登录用户
     *
     * @param userId 用户ID
     * @return 登录用户；不存在/解析失败返回 null
     */
    public LoginUser get(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(key(userId));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, LoginUser.class);
        } catch (Exception e) {
            log.warn("读取登录用户缓存失败, userId={}", userId, e);
            return null;
        }
    }

    /**
     * 删除登录用户缓存（登出/强制下线）
     *
     * @param userId 用户ID
     */
    public void delete(Long userId) {
        if (userId != null) {
            redisTemplate.delete(key(userId));
        }
    }

    private String key(Long userId) {
        return keyPrefix+":"+ userId;
    }
}
