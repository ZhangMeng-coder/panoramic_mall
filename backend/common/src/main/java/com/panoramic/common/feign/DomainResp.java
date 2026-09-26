package com.panoramic.common.feign;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.vo.RespData;

/**
 * 域响应的解包规约（内部 Feign 调用侧，全仓唯一一份）。
 * <p>业务域的内部接口统一返回 {@code RespData{code,msg,data}}（cross-cutting 第 2 条），且
 * <b>HTTP 200 承载一切业务结果</b>：正常 {@code code=200}；业务校验失败
 * {@code code=400/403/404}（域侧不抛异常，所以 Feign 的 {@code ErrorDecoder} <b>不会</b>被调用）。
 * 只有<b>真故障</b>才以 HTTP 5xx 返回：那时 Feign 直接抛 {@code FeignException}，
 * <b>走不到本类</b>——这正是熔断的失败信号（第 13 条）。</p>
 * <p>故调用方只需一个判据：{@code code == 200} 取 {@code data}，否则把 {@code code + msg}
 * 还原成 {@link ServiceException} 抛出（页面看到的是域侧原文）。</p>
 * <p>⚠ <b>端 BFF 与 trade-center 的域间调用共用这一处</b>，区别只在「抛出去之后怎么办」：
 * 端 BFF 经 {@link BffFeignCall} 把 400/403/404 透传、其余降级为「…暂不可用」；
 * 域间调用（第 24 条）<b>一律上抛、不降级</b>——store 不可达时下单必须整体失败。</p>
 */
public final class DomainResp {

    private DomainResp() {
    }

    /**
     * 解包域响应：{@code code == 200} 返回 {@code data}，否则抛 {@link ServiceException}。
     *
     * @param resp 域侧响应（{@code void} 端点解出 {@code null}）
     * @param <T>  业务结果类型
     * @return 业务数据（可能是 {@code null}，如「查不到返空」的既有语义）
     */
    public static <T> T unwrap(RespData<T> resp) {
        if (resp == null) {
            // 200 但空 body（或形状不对）：域侧新形态下不该出现，按服务器异常处理
            throw new ServiceException(500, "服务调用失败（下游返回空响应）");
        }
        Integer code = resp.getCode();
        if (code != null && code == 200) {
            return resp.getData();
        }
        return throwError(code, resp.getMsg());
    }

    private static <T> T throwError(Integer code, String msg) {
        throw new ServiceException(code == null ? 500 : code,
                (msg == null || msg.isBlank()) ? "服务调用失败" : msg);
    }
}
