package com.panoramic.common.feign;

import com.panoramic.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 端 BFF 调业务域（Feign + 熔断）的统一编排执行器。
 * <p>把「沿 cause 链剥出下游业务异常 + 非业务异常降级」这段重复逻辑收敛到一处，
 * 供 admin / store-bff 等端 BFF 复用（此前各 BFF 各持一份私有拷贝）。</p>
 *
 * <p><b>为什么要沿 cause 链找</b>：Feign + 熔断会把下游抛出的业务异常包装成
 * {@code NoFallbackAvailableException} / {@code ExecutionException} / {@code CompletionException}
 * 等再抛出，不剥壳就会把 400「已上架 SKU 不可修改」这类业务校验误判成连接故障，
 * 降级成 500「服务暂不可用」。</p>
 *
 * <p><b>4xx 透传 / 5xx 降级的分野</b>（与 {@link InternalApiErrorDecoder} 及 cross-cutting 第 13 条 2026-09-10 修正一致）：
 * <ul>
 *   <li>下游返回的业务异常（400 参数/业务、403 权限、404 不存在）：<b>原样抛出</b>，由统一异常处理还原给页面。
 *       这类错误是「调用方语义/参数问题」，不是下游健康度信号；端 BFF 的熔断器已配
 *       {@code ignore-exceptions: ServiceException}，故不计入失败率——否则店主连续几次操作失误
 *       就会打开熔断，把后续<b>正常</b>请求也降级成 500。</li>
 *   <li>其余 {@code ServiceException}（非 400/403/404）与所有非业务异常（熔断开启 / 连接失败 / 序列化失败等）：
 *       记日志后降级为 {@code ServiceException(500, degradeMessage)}，避免下游故障拖垮调用方。</li>
 * </ul></p>
 */
@Slf4j
public final class BffFeignCall {

    private BffFeignCall() {
    }

    /**
     * 执行下游调用（日志不含下游名，仅用于降级文案区分时使用）
     *
     * @param action        实际调用
     * @param degradeMessage 降级提示文案（如「店铺服务暂不可用，请稍后重试」）
     * @param <T>           业务结果类型
     * @return 下游结果
     */
    public static <T> T call(Supplier<T> action, String degradeMessage) {
        return call(null, degradeMessage, action);
    }

    /**
     * 执行下游调用
     *
     * @param downstream     下游服务名（仅日志用，可空）
     * @param degradeMessage 降级提示文案
     * @param action         实际调用
     * @param <T>            业务结果类型
     * @return 下游结果
     */
    public static <T> T call(String downstream, String degradeMessage, Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            // 沿 cause 链找下游业务异常，剥开熔断/异步包装层
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof ServiceException se) {
                    Integer code = se.getCode();
                    if (code != null && (code == 400 || code == 403 || code == 404)) {
                        // 参数/业务(400)、权限(403)、不存在(404)：原样透传，由统一异常处理还原给页面
                        throw se;
                    }
                    log.warn("{} 调用异常，降级处理: code={}, msg={}", downstream, se.getCode(), se.getMessage());
                    throw new ServiceException(500, degradeMessage);
                }
            }
            // 非业务异常：熔断开启 / 连接失败 / 序列化等 → 降级为友好提示
            log.error("{} 调用失败，降级处理", downstream, e);
            throw new ServiceException(500, degradeMessage);
        }
    }
}
