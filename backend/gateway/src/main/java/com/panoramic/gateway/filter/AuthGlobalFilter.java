package com.panoramic.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关鉴权过滤器（WebFlux，不依赖 common）
 * <p>对非白名单请求：① 验 JWT 签名/有效期 → ② 解析 type claim（缺省 admin）
 * → ③ 校验 Redis 中 {prefix}:{type}:{userId} 登录用户上下文仍有效。
 * 任一环失效一律回 HTTP 401（与业务服务安全链的 401 语义一致）；通过则把 userId、userType 分别写入
 * {@code X-User-Id} / {@code X-User-Type} 请求头透传给下游业务服务作为「查用户上下文」的 key。</p>
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /** JWT 未认证提示 */
    private static final String MSG_UNAUTHORIZED = "未登录或登录已失效";

    private static final String BEARER_PREFIX = "Bearer ";

    /** JWT type claim 名 / X-User-Type 头名（与 common LoginUser 常量对齐） */
    private static final String CLAIM_USER_TYPE = "type";
    private static final String DEFAULT_USER_TYPE = "admin";
    private static final String HEADER_USER_TYPE = "X-User-Type";

    private final SecretKey secretKey;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final String redisPrefix;
    private final String headerName;
    private final String[] whitelistPaths;

    public AuthGlobalFilter(ReactiveStringRedisTemplate redisTemplate,
                            @Value("${panoramic.auth.jwt-secret}") String secret,
                            @Value("${panoramic.auth.redis-prefix:panoramic:login:user}") String redisPrefix,
                            @Value("${panoramic.auth.header-name:X-User-Id}") String headerName,
                            @Value("${panoramic.auth.whitelist-paths:/admin/auth/login,/discovery/**}") String whitelistPaths) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
        this.redisPrefix = redisPrefix;
        this.headerName = headerName;
        this.whitelistPaths = whitelistPaths.split("\\s*,\\s*");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // CORS 预检与白名单路径直接放行
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) || isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        Claims claims = parseClaims(exchange.getRequest().getHeaders().getFirst("Authorization"));
        if (claims == null || claims.getSubject() == null) {
            return unauthorized(exchange, MSG_UNAUTHORIZED);
        }
        Long userId = Long.valueOf(claims.getSubject());
        String userType = typeOf(claims.get(CLAIM_USER_TYPE));

        String key = redisPrefix + ":" + userType + ":" + userId;
        // hasElement 判定登录态键是否存在：get 对缺失键返回空 Mono，若用 switchIfEmpty 收口，
        // flatMap 分支返回的 Mono<Void>（转发成功）会以「无值完成」被 switchIfEmpty 误判为空，
        // 导致转发提交后再跑一次 unauthorized 写响应 → ReadOnlyHttpHeaders 抛 UnsupportedOperationException。
        return redisTemplate.opsForValue().get(key)
                .hasElement()
                .flatMap(loginExists -> {
                    if (!loginExists) {
                        // Redis 无此登录用户（登出/超时/强制下线）→ 401
                        return unauthorized(exchange, MSG_UNAUTHORIZED);
                    }
                    // 通过：透传 userId + userType，供业务服务查用户上下文
                    return chain.filter(exchange.mutate()
                            .request(builder -> builder.headers(headers -> {
                                headers.set(headerName, String.valueOf(userId));
                                headers.set(HEADER_USER_TYPE, userType);
                            }))
                            .build());
                });
    }

    @Override
    public int getOrder() {
        // 先于路由具体 filter 执行
        return -100;
    }

    /**
     * 解析 token 的 payload（验签 + 过期校验）
     *
     * @param authorization Authorization 头值
     * @return Claims；缺失/非法返回 null
     */
    private Claims parseClaims(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 用户类型兜底：type claim 缺失/空 → admin
     */
    private String typeOf(Object type) {
        if (type == null || String.valueOf(type).isBlank()) {
            return DEFAULT_USER_TYPE;
        }
        return String.valueOf(type);
    }

    private boolean isWhitelisted(String path) {
        for (String pattern : whitelistPaths) {
            String p = pattern.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.endsWith("/**")) {
                if (path.startsWith(p.substring(0, p.length() - 3))) {
                    return true;
                }
            } else if (path.equals(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 回 401 JSON（结构与 RespData / 业务服务安全链一致：{code,msg}）
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"" + msg + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
