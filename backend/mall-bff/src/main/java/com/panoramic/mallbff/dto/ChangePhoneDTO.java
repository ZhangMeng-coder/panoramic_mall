package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * C 端换绑手机号请求参数（旧号码 + 新号码双验证）
 * <p>⚠ <b>双验证是完整流程的一部分，不因短信通道是模拟实现而省略</b>：旧号码验证码证明「是本人在操作」，
 * 新号码验证码证明「新号确实可用/可达」。两处都走 {@code AuthService#assertCode}，接真实短信通道时自动生效。</p>
 * <p>手机号格式规则与 {@code RegisterDTO#phone} <b>逐字一致</b>（同一账号体系，两处不同步就是一处能注册、换绑不了的错配）。</p>
 */
@Data
public class ChangePhoneDTO {

    /**
     * 旧号码（当前登录账号手机号）收到的验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String oldCode;

    /**
     * 新手机号（换绑目标，换绑后即为登录账号）
     */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String newPhone;

    /**
     * 新号码收到的验证码
     */
    @NotBlank(message = "验证码不能为空")
    private String newCode;
}
