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
 * JWT 服务：签发（admin/店主登录）与解析（common AuthFilter 兜底取 userId / userType）。
 * <p>约定：token 只携带 userId（subject）与用户类型（type claim）两个维度，
 * 不塞其它业务信息——用户上下文统一从 Redis 按 {type}:{userId} 取。</p>
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
     * 生成 token（用户类型取默认 admin，兼容旧调用）
     *
     * @param userId 用户ID
     * @return JWT 字符串
     */
    public String generateToken(Long userId) {
        return generateToken(userId, LoginUser.USER_TYPE_ADMIN);
    }

    /**
     * 生成 token：subject = userId，claim type = userType，过期时间按配置
     *
     * @param userId   用户ID
     * @param userType 用户类型（admin/store，null 视为 admin）
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String userType) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireSeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(LoginUser.CLAIM_USER_TYPE,
                        userType == null || userType.isBlank() ? LoginUser.USER_TYPE_ADMIN : userType)
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
        Claims claims = parse(token);
        return claims == null ? null : Long.valueOf(claims.getSubject());
    }

    /**
     * 解析 token 得到用户类型（type claim）
     *
     * @param token JWT 字符串
     * @return userType；缺省/解析失败默认 admin
     */
    public String parseUserType(String token) {
        Claims claims = parse(token);
        if (claims == null) {
            return LoginUser.USER_TYPE_ADMIN;
        }
        Object type = claims.get(LoginUser.CLAIM_USER_TYPE);
        return type == null || String.valueOf(type).isBlank() ? LoginUser.USER_TYPE_ADMIN : String.valueOf(type);
    }

    /**
     * 验签+过期校验并返回 payload；失败返回 null
     */
    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
