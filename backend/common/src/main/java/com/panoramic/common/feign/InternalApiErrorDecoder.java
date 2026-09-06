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
 * <p>业务域遵循规约「错误走异常/统一处理传播」：对下游非 2xx 响应，若 body 是
 * {@code {code,msg}} 同构 JSON，则还原为 {@link ServiceException}（含业务码与提示），由端 BFF
 * 透传给页面；无法解析/连接类错误回落 {@link feign.codec.ErrorDecoder.Default} 抛 Feign 异常，
 * 由调用方统一降级处理。</p>
 */
public class InternalApiErrorDecoder implements ErrorDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        String body = readBody(response);
        Integer code = extractInt(body, "code");
        String msg = extractText(body, "msg");
        if (code != null) {
            return new ServiceException(code,
                    (msg == null || msg.isBlank()) ? "服务调用失败(" + response.status() + ")" : msg);
        }
        // 非标准错误体：交给默认解码（产出 FeignException，供调用方降级）
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
