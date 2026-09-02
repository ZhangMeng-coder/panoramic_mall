package com.panoramic.common.exception;

import com.panoramic.common.enums.ServiceExceptionEnums;
import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException{

    private Integer code;
    private String message;

    public ServiceException(ServiceExceptionEnums serviceExceptionEnums) {
        super(serviceExceptionEnums.getMessage());
        this.code = serviceExceptionEnums.getCode();
        this.message = serviceExceptionEnums.getMessage();
    }

    public ServiceException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public ServiceException(String message) {
        this(400, message);
    }
}
