package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * C 端登录请求参数（手机号 + 短信验证码，无密码）
 */
@Data
public class LoginDTO {

    /**
     * 手机号（登录账号）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 短信验证码（模拟通道，固定码 888888）
     */
    @NotBlank(message = "验证码不能为空")
    private String code;
}
