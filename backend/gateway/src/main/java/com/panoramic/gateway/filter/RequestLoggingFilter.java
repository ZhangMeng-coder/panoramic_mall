package com.panoramic.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * 网关请求日志过滤器（WebFlux 响应式）
 * <p>在过滤器链最前端记录每个请求的入站信息（方法、路径、查询参数），
 * 待响应回写完成后记录响应状态码和处理耗时（毫秒），方便线上问题排查与性能监控。</p>
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String method = request.getMethod().name();
        String path = uri.getPath();
        String query = uri.getQuery();

        // 构建请求日志：GET /api/xxx?foo=bar
        String requestLog = method + " " + path + (query != null ? "?" + query : "");
        log.info("→ 请求: {}", requestLog);

        long startTime = System.currentTimeMillis();

        return chain.filter(exchange)
                .doFinally(signalType -> {
                    long elapsed = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    log.info("← 响应: {} {} {}ms [{}]", status, requestLog, elapsed, signalType);
                });
    }

    @Override
    public int getOrder() {
        // 最先执行：在 BffRouteGuardFilter(-200) / AuthGlobalFilter(-100) 之前
        return Ordered.HIGHEST_PRECEDENCE;
    }
}