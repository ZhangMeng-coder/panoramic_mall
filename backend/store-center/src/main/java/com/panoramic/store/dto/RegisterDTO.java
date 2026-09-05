package com.panoramic.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 店主注册请求参数
 */
@Data
public class RegisterDTO {

    /**
     * 用户名（登录账号）
     */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_]{3,50}$", message = "用户名需为3-50位字母、数字或下划线")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64位之间")
    private String password;

    /**
     * 昵称/姓名
     */
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    /**
     * 手机号
     */
    @Size(max = 20, message = "手机号不能超过20个字符")
    private String phone;
}
