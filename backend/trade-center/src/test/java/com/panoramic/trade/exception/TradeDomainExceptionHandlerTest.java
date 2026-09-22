package com.panoramic.trade.exception;

import com.panoramic.common.vo.RespData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「调用方入参错误」的两类 MVC 异常 → **400**，而不是兜底的 500。
 *
 * <p>⚠ 守的是哪条默认路径：{@code MissingServletRequestParameterException} /
 * {@code MethodArgumentTypeMismatchException} 都**不是** {@code IllegalArgumentException} 的子类，
 * 不显式接住就落到域兜底 {@code Exception} → 500。同一件事于是有两种失败模式
 * （DTO 字段漏传 → 400，裸 {@code @RequestParam} 漏传 → 500），
 * 且 5xx 会被端 BFF 按 cross-cutting 第 13 条**计入熔断失败率**——调用方漏一个参数就能打开熔断。
 * 故这里钉住状态码：4xx（页面透传、不计熔断），不是 5xx（真故障才该有的形状）。</p>
 *
 * <p>⚠ 本类不起 Spring：直接调处理器方法，断的是「这两类各自映射成什么状态码、文案里带不带参数名」；
 * 「Spring 在什么情况下抛这两类」是框架语义，不在这里重验。</p>
 */
class TradeDomainExceptionHandlerTest {

    private final TradeDomainExceptionHandler handler = new TradeDomainExceptionHandler();

    @Test
    @DisplayName("缺必填的 @RequestParam → 400，文案带参数名")
    void missingRequestParamIsBadRequest() {
        ResponseEntity<RespData<Void>> resp = handler.handleMissingParameter(
                new MissingServletRequestParameterException("customerId", "Long"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMsg()).contains("customerId");
    }

    @Test
    @DisplayName("参数取值类型不符（如 customerId=abc）→ 400，文案带参数名")
    void typeMismatchIsBadRequest() throws Exception {
        Method handle = Sample.class.getDeclaredMethod("handle", Long.class);
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "customerId", new MethodParameter(handle, 0),
                new IllegalArgumentException("NumberFormatException"));

        ResponseEntity<RespData<Void>> resp = handler.handleTypeMismatch(e);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getMsg()).contains("customerId");
    }

    @Test
    @DisplayName("兜底仍是 500：别把真故障一并改成 4xx（那会让熔断永远打不开）")
    void fallbackStaysInternalServerError() {
        ResponseEntity<RespData<Void>> resp = handler.handleException(new IllegalStateException("boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** 仅为构造 {@link MethodParameter} 而存在的宿主方法 */
    @SuppressWarnings("unused")
    private static class Sample {
        void handle(Long customerId) {
        }
    }
}
