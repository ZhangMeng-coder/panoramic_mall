package com.panoramic.common.vo;


import com.panoramic.common.exception.ServiceException;
import lombok.Getter;

/**
 * 响应数据
 *
 * @param <T> 数据类型
 */
@Getter
public class RespData<T> {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应信息
     */
    private String msg;

    /**
     * 响应数据
     */
    private T data;

    public RespData(Integer code,  String msg, T data){
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public RespData(Integer code,  String msg){
        this.code = code;
        this.msg = msg;
    }

    public static <T> RespData<T> success(T data){
        return new RespData<>(200, "success", data);
    }

    public static <T> RespData<T> success(){
        return new RespData<>(200, "success");
    }

    public static <T> RespData<T> error(Integer code, String msg){
        return new RespData<>(code, msg);
    }

    public static <T> RespData<T> error(ServiceException serviceException){
        return new RespData<>(serviceException.getCode(), serviceException.getMessage());
    }
}
