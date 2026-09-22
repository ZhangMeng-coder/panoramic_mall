package com.panoramic.trade.exception;

import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.vo.RespData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 交易域（内部接口）异常处理器。
 * <p>trade-center 域已下沉为纯域、无页面消费者，统一把异常还原为<b>真实 HTTP 状态码</b>
 * + {@code {code,msg}} 响应，供内部 Feign 调用方的错误解码器识别并还原业务异常。
 * 以最高优先级覆盖 common 的页面态异常处理（common 保持 HTTP 200 + code 语义，供仍面向页面的端 BFF 使用）。</p>
 * <p>⚠ <b>兜底 {@code Exception} → HTTP 500 这一形状是跨服务契约，不得改成 200 或 400</b>
 * （见 docs/contracts/cross-cutting.md 第 13 条）：调用方（mall-bff 经 {@code InternalApiErrorDecoder}）
 * 按 HTTP 状态码分野——<b>4xx</b> 才还原成 {@code ServiceException}（不计熔断失败率、原样透传给页面），
 * <b>5xx</b> 回落 {@code FeignException}（照常计入失败率 → 真故障时熔断该打开就打开）。
 * 若把这里的兜底也写成 4xx（或把业务码 500 当 4xx 还原），连真故障都会被熔断忽略，熔断永远打不开。</p>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TradeDomainExceptionHandler {

    /**
     * 业务异常：code 落在 HTTP 语义区间则直接作状态码（400/403/404/500…），否则按 500 处理
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RespData<Void>> handleServiceException(ServiceException e) {
        log.warn("交易域业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        int httpStatus = toHttpStatus(e.getCode());
        return ResponseEntity.status(httpStatus).body(RespData.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespData<Void>> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return badRequest(msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespData<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return badRequest(msg);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<RespData<Void>> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return badRequest(msg.isBlank() ? "请求参数错误" : msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespData<Void>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return badRequest("请求参数格式错误");
    }

    /**
     * 缺必填的请求参数（如漏传 {@code customerId}）→ <b>400</b>
     *
     * <p>⚠ 这两类（缺参 / 取值类型不符）在 Spring 里都不是 {@code IllegalArgumentException} 的子类，
     * 不显式接住就会落到下面的兜底 {@code Exception} → **500**。后果有两层：
     * ① 同一件事出现两种失败模式——走 DTO 的接口漏传字段回 400（{@code @NotNull} 拦的），
     * 走裸 {@code @RequestParam} 的漏传却回 500；
     * ② 5xx 会被端 BFF 按 cross-cutting 第 13 条**计入熔断失败率**，调用方漏一个参数就可能把熔断打开，
     * 后续正常请求全被降级——那是真故障才该有的后果。故「调用方漏传参数」一律 400。</p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RespData<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        return badRequest("缺少请求参数：" + e.getParameterName());
    }

    /**
     * 参数取值无法转成目标类型（如 {@code customerId=} 空串转 {@code Long}、{@code /items/abc}) → <b>400</b>
     *
     * <p>判据同上：它是调用方的入参错误，不是服务故障。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespData<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return badRequest("请求参数取值非法：" + e.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespData<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return badRequest(e.getMessage() == null || e.getMessage().isBlank()
                ? ServiceExceptionEnums.PARAM_ERROR.getMessage() : e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespData<Void>> handleException(Exception e) {
        log.error("交易域系统异常: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespData.error(500, "系统内部错误，请联系管理员"));
    }

    private ResponseEntity<RespData<Void>> badRequest(String msg) {
        int code = ServiceExceptionEnums.PARAM_ERROR.getCode();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(RespData.error(code, msg));
    }

    private int toHttpStatus(Integer code) {
        int c = code == null ? 500 : code;
        return c >= 400 && c <= 599 ? c : HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}
