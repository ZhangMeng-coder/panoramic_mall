package com.panoramic.common.goods.api;

import com.panoramic.common.feign.InternalApiErrorDecoder;
import com.panoramic.common.feign.InternalHeaders;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * goods-center 内部 Feign 客户端配置（随 {@link GoodsCenterClient} 的 configuration 生效）。
 * <p>仅作为 Feign 客户端配置类被引用，不参与组件扫描（避免全局作用到所有 Feign 客户端）。
 * <ul>
 *   <li>{@link RequestInterceptor}：为每个出站内部调用附带信任头，并把主身份（X-User-Id）与
 *       用户类型（X-User-Type）透传给下游（优先取当前 Web 请求上 gateway 透传的身份头，经
 *       RequestContextHolder 跨熔断线程读取；兜底取请求线程 UserContext），供 goods-center 经
 *       Redis 重建登录用户做最终范围判定；</li>
 *   <li>{@link ErrorDecoder}：把下游非 2xx 的 {@code {code,msg}} 响应还原为业务异常。</li>
 * </ul></p>
 */
public class GoodsFeignConfiguration {

    @Bean
    public RequestInterceptor goodsInternalRequestInterceptor(
            @Value("${panoramic.auth.header-name:X-User-Id}") String userIdHeader,
            @Value("${panoramic.internal.secret:panoramic-mall-internal-dev-token}") String trustToken) {
        return template -> {
            // 信任头 + 范围（预留）；令牌与 goods-center 本地校验值一致，勿单边改动
            template.header(InternalHeaders.TRUST_TOKEN, trustToken);
            template.header(InternalHeaders.TRUST_SCOPE, InternalHeaders.SCOPE_PLATFORM);
            // 主身份透传。⚠ 不能只读 UserContext(ThreadLocal)：Feign 熔断会把调用挪到其它线程执行，
            // 自定义 ThreadLocal 不会随线程迁移；而 OpenFeign 会在熔断线程上恢复 RequestContextHolder，
            // 故主身份以「当前 Web 请求上 gateway 透传的 X-User-Id/X-User-Type」为首选来源。
            String userId = null;
            String userType = null;
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
                HttpServletRequest request = servletRequestAttributes.getRequest();
                if (request != null) {
                    userId = request.getHeader(userIdHeader);
                    userType = request.getHeader(LoginUser.HEADER_USER_TYPE);
                }
            }
            // 兜底：无 Web 请求上下文（如同步调用/直连测试）时读请求线程 ThreadLocal 登录用户
            if (userId == null || userId.isBlank()) {
                LoginUser loginUser = UserContext.getLoginUser();
                if (loginUser != null && loginUser.getId() != null) {
                    userId = String.valueOf(loginUser.getId());
                    userType = loginUser.getUserType();
                }
            }
            if (userId != null && !userId.isBlank()) {
                template.header(userIdHeader, userId);
                template.header(LoginUser.HEADER_USER_TYPE,
                        userType == null || userType.isBlank() ? LoginUser.USER_TYPE_ADMIN : userType);
            }
        };
    }

    @Bean
    public ErrorDecoder goodsInternalErrorDecoder() {
        return new InternalApiErrorDecoder();
    }
}
