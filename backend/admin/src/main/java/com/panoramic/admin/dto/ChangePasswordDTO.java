package com.panoramic.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求参数
 */
@Data
public class ChangePasswordDTO {

    /**
     * 原密码
     */
    @NotBlank(message = "原密码不能为空")
    @Size(max = 64, message = "原密码长度不能超过64个字符")
    private String oldPassword;

    /**
     * 新密码（与用户新建/编辑密码规则一致：6-64）
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在6-64之间")
    private String newPassword;
}
