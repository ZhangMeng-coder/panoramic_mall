package com.panoramic.common.exception;

import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.vo.RespData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
     * 处理参数校验异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public RespData<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.error("请求地址: {}, 参数校验异常: {}", request.getRequestURI(), e.getMessage());
        return RespData.error(ServiceExceptionEnums.PARAM_ERROR.getCode(), e.getMessage());
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
     * 处理其他未知异常
     */
    @ExceptionHandler(Exception.class)
    public RespData<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("请求地址: {}, 系统异常: ", request.getRequestURI(), e);
        return RespData.error(500, "系统内部错误，请联系管理员");
    }
}
