package com.panoramic.common.store.api;

import com.panoramic.common.feign.InternalApiErrorDecoder;
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
 * store 域内部 Feign 客户端配置（随 {@link StoreClient} 的 configuration 生效）。
 * <p>仅作为 Feign 客户端配置类被引用，不参与组件扫描（避免全局作用到所有 Feign 客户端）。
 * <ul>
 *   <li>{@link RequestInterceptor}：为每个出站内部调用把主身份（X-User-Id）与用户类型
 *       （X-User-Type）<b>原样透传</b>给下游（优先取当前 Web 请求上 gateway 透传的身份头，经
 *       RequestContextHolder 跨熔断线程读取；兜底取请求线程 UserContext），供 store 域填审计字段
 *       与做 owner/platform 分流；⚠ 不做 goods 版「缺省回退 admin」的写死兜底——店主侧（store-bff）
 *       调用若被盖成 admin，会使 store 域 owner 分支（X-User-Type=store）失效，故缺省就缺省，
 *       交给下游按缺省语义处理（store-bff 侧 owner 方法实际会显式传 store_id，不依赖此兜底）；</li>
 *   <li>{@link ErrorDecoder}：把下游非 2xx 的 {@code {code,msg}} 响应还原为业务异常。</li>
 * </ul></p>
 */
public class StoreFeignConfiguration {

    @Bean
    public RequestInterceptor storeInternalRequestInterceptor(
            @Value("${panoramic.auth.header-name:X-User-Id}") String userIdHeader) {
        return template -> {
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
                // X-User-Type 原样透传，不做缺省回退（避免店主侧被盖成 admin）
                if (userType != null && !userType.isBlank()) {
                    template.header(LoginUser.HEADER_USER_TYPE, userType);
                }
            }
        };
    }

    @Bean
    public ErrorDecoder storeInternalErrorDecoder() {
        return new InternalApiErrorDecoder();
    }
}
