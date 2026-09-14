package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * C 端注册请求参数（注册即登录）
 */
@Data
public class RegisterDTO {

    /**
     * 手机号（注册后即为登录账号）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 短信验证码（模拟通道，固定码 888888）
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 昵称（可空，空则默认取手机号）
     */
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;
}
