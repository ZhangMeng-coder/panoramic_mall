package com.panoramic.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

/**
 * 网关「公网入口=BFF」路由守卫（WebFlux，不依赖 common）
 * <p>网关是唯一公网入口，语义上只允许把请求转发给「端 BFF」服务；业务域服务（goods-center 等）
 * 只被 BFF 经注册中心内部 Feign 调用，不可经网关被页面直连。</p>
 * <p>实现为白名单守卫：匹配到的路由若转发到 {@code panoramic.gateway.bff-services}（lb:// 服务名）
 * 之外的任何上游，一律回 HTTP 403。即便后续误加了一条指向域服务的路由，也会被这里默认拒绝，
 * 而不是默默暴露成公网入口。仅接受 lb 路由；非 lb 直连路由视为非 BFF 入口一并拒绝。</p>
 * <p>白名单不写死、放配置：store-center 目前兼店铺端后端（店铺端 BFF 角色），待其拆出 store-bff 后，
 * 把名单里的 store-center 换成 store-bff 即可。</p>
 */
@Component
public class BffRouteGuardFilter implements GlobalFilter, Ordered {

    /** 拒绝提示 */
    private static final String MSG_FORBIDDEN = "服务不可经网关公开访问（网关仅转发端 BFF 服务）";

    /** 允许经网关对外暴露的端 BFF 服务名（lb:// 主机名） */
    private final Set<String> bffServices;

    public BffRouteGuardFilter(@Value("${panoramic.gateway.bff-services:}") String bffServices) {
        // 逗号分隔；配置为空 = 不信任任何上游（默认拒绝，由 application.yml 显式声明白名单）
        this.bffServices = Arrays.stream(bffServices.split("\\s*,\\s*"))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        if (route == null) {
            // 未命中任何路由（如网关自身 /discovery 控制器路径）：不由本守卫接管，交给后续处理（404/控制器）
            return chain.filter(exchange);
        }
        URI uri = route.getUri();
        // 仅放行 lb://<白名单服务>；非 lb 或名单外服务一律拒绝
        boolean allowed = uri != null
                && "lb".equalsIgnoreCase(uri.getScheme())
                && bffServices.contains(uri.getHost());
        if (!allowed) {
            return forbidden(exchange, uri == null ? route.getId() : uri.toString());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 先于鉴权（-100）执行：非白名单上游不进入鉴权/转发链路
        return -200;
    }

    /**
     * 回 403 JSON（结构与 RespData / 业务服务安全链一致：{code,msg}）
     */
    private Mono<Void> forbidden(ServerWebExchange exchange, String target) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":403,\"msg\":\"" + MSG_FORBIDDEN + " [" + target + "]\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
