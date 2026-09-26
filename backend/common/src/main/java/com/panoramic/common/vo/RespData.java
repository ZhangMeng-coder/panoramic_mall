package com.panoramic.common.vo;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.panoramic.common.exception.ServiceException;
import lombok.Getter;

/**
 * 响应数据
 * <p>页面级接口（端 BFF 出口）与内部 Feign 接口（业务域出口）<b>统一</b>用它承载结果，
 * 见 docs/contracts/cross-cutting.md 第 1、2 条。</p>
 * <p>⚠ 2026-09-26 起它也要被 <b>反序列化</b>（域侧出参 → 端 BFF / trade-center 解包），
 * 故 3 参构造标了 {@link JsonCreator}：本类无无参构造且有两个构造函数，不标注解时
 * Jackson 报 "no Creators, like default constructor, exist"，Feign 解不出域响应。</p>
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

    @JsonCreator
    public RespData(@JsonProperty("code") Integer code,
                    @JsonProperty("msg") String msg,
                    @JsonProperty("data") T data){
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
