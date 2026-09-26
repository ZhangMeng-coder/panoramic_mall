package com.panoramic.common.exception;

import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.vo.RespData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 ServiceException 异常
     */
    @ExceptionHandler(ServiceException.class)
    public RespData<Void> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.error("请求地址: {}, 业务异常: {}", request.getRequestURI(), e.getMessage());
        return RespData.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（Path Variables 和 Request Params）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public RespData<Void> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        log.error("请求地址: {}, 参数校验异常: {}", request.getRequestURI(), e.getMessage());
        
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(), errorMessage);
    }

    /**
     * 处理 @Valid 验证异常（RequestBody）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public RespData<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.error("请求地址: {}, 参数校验异常: {}", request.getRequestURI(), e.getMessage());

        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(), errorMessage);
    }

    /**
     * 处理表单/查询对象绑定异常（{@code @Validated} 在非 {@code @RequestBody} 入参上失败时抛这个）。
     * <p>与 {@link #handleMethodArgumentNotValidException} 同一语义、不同异常类型（Spring 的两条路径）。</p>
     */
    @ExceptionHandler(BindException.class)
    public RespData<Void> handleBindException(BindException e, HttpServletRequest request) {
        log.error("请求地址: {}, 参数绑定异常: {}", request.getRequestURI(), e.getMessage());

        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(),
                errorMessage.isBlank() ? "请求参数错误" : errorMessage);
    }

    /**
     * 缺必填的请求参数（如裸 {@code @RequestParam} 漏传）→ <b>400 业务码</b>。
     * <p>⚠ 它与下面那条取值类型不符**都不是** {@code IllegalArgumentException} 的子类，不显式接住就会落到
     * 兜底 {@code Exception} → **HTTP 500**。后果有两层：① 同一件事两种失败模式（走 DTO 的漏传字段回 400，
     * 走裸参数的漏传回 500）；② 5xx 是熔断的失败信号（第 13 条），**调用方漏一个参数就能把熔断打开**，
     * 后续正常请求全被降级。故「调用方入参错误」一律 400 业务码，永不落兜底。</p>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public RespData<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e,
                                                                       HttpServletRequest request) {
        log.error("请求地址: {}, 缺少请求参数: {}", request.getRequestURI(), e.getParameterName());
        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(), "缺少请求参数：" + e.getParameterName());
    }

    /**
     * 参数取值无法转成目标类型（如 {@code customerId=abc}、{@code /items/abc}）→ <b>400 业务码</b>；
     * 判据同 {@link #handleMissingServletRequestParameterException}。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public RespData<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
                                                                   HttpServletRequest request) {
        log.error("请求地址: {}, 请求参数取值非法: {}", request.getRequestURI(), e.getName());
        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(), "请求参数取值非法：" + e.getName());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public RespData<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.error("请求地址: {}, 参数校验异常: {}", request.getRequestURI(), e.getMessage());
        // 空 message 要兜底：RespData.error(code, null) 会让页面拿到空提示（域 handler 原本也这么兜）
        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(),
                e.getMessage() == null || e.getMessage().isBlank()
                        ? ServiceExceptionEnums.PARAM_ERROR.getMessage()
                        : e.getMessage());
    }

    /**
     * 处理请求体 JSON 解析异常（编码/格式错误，属客户端问题）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public RespData<Void> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.error("请求地址: {}, 请求体解析失败: {}", request.getRequestURI(), e.getMessage());
        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(), "请求参数格式错误");
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public RespData<Void> handleNotFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        log.error("请求地址: {}, 404异常: {}", request.getRequestURI(), e.getMessage());
        return RespData.error(404, "请求的资源不存在");
    }

    /**
     * 处理403权限不足（已登录但无权限）
     */
    @ExceptionHandler(AccessDeniedException .class)
    public RespData<Void> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        log.error("请求地址: {}, 权限不足: {}", request.getRequestURI(), e.getMessage());
        return RespData.error(403, "权限不足");
    }

    /**
     * 处理其他未知异常 → <b>HTTP 500 + {code,msg}</b>。
     * <p>⚠ <b>这个 5xx 是跨服务契约，不得改成 200 或 4xx</b>（cross-cutting 第 13 条）：业务域与端 BFF
     * 共用本处理器后，它是调用方（内部 Feign）唯一能拿到的「真故障」信号——域侧业务错误一律走
     * {@code ServiceException} 分支的 HTTP 200 + code，只有落到这里的才是故障，由
     * {@code FeignException} 计入熔断失败率。若这里也返 200，熔断将永远打不开、故障直接拖垮调用方。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespData<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("请求地址: {}, 系统异常: ", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespData.error(500, "系统内部错误，请联系管理员"));
    }
}
