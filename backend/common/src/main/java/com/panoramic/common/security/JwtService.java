package com.panoramic.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 服务：签发（admin 登录）与解析（common AuthFilter 兜底取 userId）。
 * <p>约定：token 只携带 userId（subject），不塞其它业务信息——用户上下文统一从 Redis 按 userId 取。</p>
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtService(@Value("${panoramic.auth.jwt-secret}") String secret,
                      @Value("${panoramic.auth.jwt-expire-seconds:7200}") long expireSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("panoramic.auth.jwt-secret 长度需 ≥ 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /**
     * 生成 token：subject = userId，过期时间按配置
     *
     * @param userId 用户ID
     * @return JWT 字符串
     */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(exp)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析 token 得到 userId（验签+过期校验）
     *
     * @param token JWT 字符串
     * @return userId；签名无效/过期/格式错误返回 null
     */
    public Long parseUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
