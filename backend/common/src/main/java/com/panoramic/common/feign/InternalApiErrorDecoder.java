package com.panoramic.common.feign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.common.exception.ServiceException;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;

/**
 * 内部调用（Feign）错误解码器。
 * <p>业务域遵循规约「错误走异常/统一处理传播」：对下游 <b>4xx</b> 响应，若 body 是
 * {@code {code,msg}} 同构 JSON，则还原为 {@link ServiceException}（含业务码与提示），由端 BFF
 * 透传给页面；其余（<b>5xx</b>、非标准 body、连接类错误）回落
 * {@link feign.codec.ErrorDecoder.Default} 抛 Feign 异常，由调用方统一降级处理。</p>
 * <p><b>⚠ 为什么必须按状态码分流 4xx / 5xx</b>：调用方（端 BFF）配了 resilience4j 熔断，
 * 并按异常类型决定是否计入失败率（见各 BFF {@code application.yml} 的
 * {@code resilience4j.circuitbreaker.configs.default.ignore-exceptions}）：
 * <ul>
 *   <li><b>4xx</b>（含域内业务校验失败）= 调用方语义/参数错误，<b>不是下游健康度信号</b>。
 *       还原为 {@link ServiceException} 后由熔断忽略——否则店主连续几次操作失误（如反复改已上架 SKU）
 *       就会把熔断打开，把后续正常请求也打成「服务暂不可用」。</li>
 *   <li><b>5xx</b> = 下游故障（域内兜底 {@code @ExceptionHandler(Exception.class)} 返回的正是
 *       <b>HTTP 500 + {code,msg}</b>）。<b>绝不能也还原成 {@link ServiceException}</b>——
 *       那样会连真故障一起被熔断忽略，熔断将永远不打开、保护形同虚设。故 5xx 一律走 Default 产出
 *       {@code FeignException}，照常计入失败率。</li>
 * </ul>
 * 判定以 <b>HTTP 状态码</b>为准（而非 body 里的 code），避免下游状态码与 body code 不一致时误判。</p>
 */
public class InternalApiErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        Integer code = extractInt(body, "code");
        String msg = extractText(body, "msg");
        // 仅 4xx + 标准错误体还原为业务异常（熔断侧忽略）；5xx 落到 Default 走 FeignException（计入失败率）
        if (response.status() < 500 && code != null) {
            return new ServiceException(code,
                    (msg == null || msg.isBlank()) ? "服务调用失败(" + response.status() + ")" : msg);
        }
        // 5xx / 非标准错误体：交给默认解码（产出 FeignException，供调用方降级、并计入熔断失败率）
        return new Default().decode(methodKey, response);
    }

    private String readBody(Response response) {
        if (response == null || response.body() == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(response.body().asReader(StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Integer extractInt(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json).get(field);
            return node != null && node.canConvertToInt() ? node.intValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractText(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json).get(field);
            return node != null && !node.isNull() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
