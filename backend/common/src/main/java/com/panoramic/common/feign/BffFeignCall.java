package com.panoramic.common.feign;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.vo.RespData;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 端 BFF 调业务域（Feign + 熔断）的统一编排执行器。
 * <p>把「解包域响应 + 沿 cause 链剥出下游业务异常 + 非业务异常降级」这段重复逻辑收敛到一处，
 * 供 admin / store-bff / mall-bff 等端 BFF 复用。域侧出参一律是 {@code RespData<T>}（第 2 条），
 * 解包走 {@link DomainResp#unwrap}（{@code code != 200} → {@code ServiceException(code,msg)}）。</p>
 *
 * <p><b>为什么入参是 {@code Supplier<RespData<T>>} 而不是 {@code Supplier<T>}</b>：解包必须在
 * <b>本方法内</b>发生，才能与 {@code try} 共用同一个出口——若让调用方自己在 lambda 里
 * {@code DomainResp.unwrap(...)}，解包抛出的 {@code ServiceException} 就落在 {@code try} 之外，
 * 下游业务错误会被当成本层异常上抛，不再走下面的透传/降级判据。</p>
 *
 * <p><b>为什么要沿 cause 链找</b>：Feign + 熔断会把下游抛出的异常包装成
 * {@code NoFallbackAvailableException} / {@code ExecutionException} / {@code CompletionException}
 * 等再抛出，不剥壳就会把「已上架 SKU 不可修改」这类业务校验误判成连接故障，
 * 降级成 500「服务暂不可用」。</p>
 *
 * <p><b>透传 / 降级的分野</b>（与 {@link DomainResp} 及 cross-cutting 第 13 条一致）：
 * <ul>
 *   <li>业务错误 {@code 400} 参数/业务、{@code 403} 权限、{@code 404} 不存在：<b>原样抛出</b>，
 *       由统一异常处理还原给页面。这类错误是「调用方语义/参数问题」，不是下游健康度信号，
 *       也不会计入熔断失败率（域侧用 HTTP 200 + code 表达，它连异常都不是）。</li>
 *   <li>其余 {@code ServiceException}（非 400/403/404）与所有非业务异常（下游 5xx / 熔断开启 /
 *       连接失败 / 序列化失败等）:记日志后降级为 {@code ServiceException(500, degradeMessage)}，
 *       避免下游故障拖垮调用方。</li>
 * </ul></p>
 *
 * <p>⚠ <b>域间调用不用本类</b>：trade-center 的两个适配器直接 {@link DomainResp#unwrap}、
 * <b>不降级</b>——store 不可达时下单必须整体失败，降级会产出「没扣库存的订单」（第 24 条）。</p>
 */
@Slf4j
public final class BffFeignCall {

    private BffFeignCall() {
    }

    /**
     * 执行下游调用并解包域响应
     *
     * @param downstream     下游服务名（仅日志用，可空）
     * @param degradeMessage 降级提示文案（如「店铺服务暂不可用，请稍后重试」）
     * @param action         实际调用（域侧出参是 {@code RespData<T>}）
     * @param <T>            业务结果类型
     * @return 解包后的业务数据（{@code code == 200} 时的 {@code data}）
     */
    public static <T> T call(String downstream, String degradeMessage, Supplier<RespData<T>> action) {
        try {
            return DomainResp.unwrap(action.get());
        } catch (Exception e) {
            throw degrade(e, downstream, degradeMessage);
        }
    }

    /**
     * 统一降级出口：业务 4xx 原样返回（由调用方 {@code throw}），其余记日志后降级为 500 文案。
     * <p>返回而非直接抛，是为了让入口与判据分开（本方法内不抛，交由 {@link #call} 抛出）。</p>
     */
    private static ServiceException degrade(Throwable e, String downstream, String degradeMessage) {
        // 沿 cause 链找下游业务异常，剥开熔断/异步包装层
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ServiceException se) {
                Integer code = se.getCode();
                if (code != null && (code == 400 || code == 403 || code == 404)) {
                    // 参数/业务(400)、权限(403)、不存在(404)：原样透传，由统一异常处理还原给页面
                    return se;
                }
                log.warn("{} 调用异常，降级处理: code={}, msg={}", downstream, se.getCode(), se.getMessage());
                return new ServiceException(500, degradeMessage);
            }
        }
        // 非业务异常：下游 5xx / 熔断开启 / 连接失败 / 序列化等 → 降级为友好提示
        log.error("{} 调用失败，降级处理", downstream, e);
        return new ServiceException(500, degradeMessage);
    }
}
