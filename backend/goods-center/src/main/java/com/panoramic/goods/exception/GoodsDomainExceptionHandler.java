package com.panoramic.goods.exception;

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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 商品域（内部接口）异常处理器。
 * <p>goods-center 已下沉为纯域、无页面消费者，统一把异常还原为<b>真实 HTTP 状态码</b>
 * + {@code {code,msg}} 响应，供内部 Feign 调用方的错误解码器识别并还原业务异常。
 * 以最高优先级覆盖 common 的页面态异常处理（common 保持 HTTP 200 + code 语义，供仍面向页面的端 BFF 使用）。</p>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GoodsDomainExceptionHandler {

    /**
     * 业务异常：code 落在 HTTP 语义区间则直接作状态码（400/403/404/500…），否则按 500 处理
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RespData<Void>> handleServiceException(ServiceException e) {
        log.warn("商品域业务异常: code={}, msg={}", e.getCode(), e.getMessage());
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespData<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return badRequest(e.getMessage() == null || e.getMessage().isBlank()
                ? ServiceExceptionEnums.PARAM_ERROR.getMessage() : e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespData<Void>> handleException(Exception e) {
        log.error("商品域系统异常: ", e);
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
