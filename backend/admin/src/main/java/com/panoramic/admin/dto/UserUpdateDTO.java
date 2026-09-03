package com.panoramic.admin.dto;

import com.panoramic.common.valid.ValidationGroups;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户请求参数
 */
@Data
public class UserUpdateDTO {

    /**
     * 用户名（登录账号）
     */
    @NotBlank(message = "用户名不能为空", groups = ValidationGroups.Update.class)
    @Size(max = 50, message = "用户名不能超过50个字符", groups = ValidationGroups.Update.class)
    private String username;

    /**
     * 密码（可选；为空或空白表示不修改）
     */
    @Size(min = 6, max = 64, message = "密码长度需在6-64之间", groups = ValidationGroups.Update.class)
    private String password;

    /**
     * 昵称/姓名
     */
    @Size(max = 50, message = "昵称不能超过50个字符", groups = ValidationGroups.Update.class)
    private String nickname;

    /**
     * 手机号
     */
    @Size(max = 20, message = "手机号不能超过20个字符", groups = ValidationGroups.Update.class)
    private String phone;

    /**
     * 邮箱
     */
    @Size(max = 100, message = "邮箱不能超过100个字符", groups = ValidationGroups.Update.class)
    private String email;

    /**
     * 头像 URL
     */
    @Size(max = 255, message = "头像URL不能超过255个字符", groups = ValidationGroups.Update.class)
    private String avatar;

    /**
     * 状态：1 启用，0 停用
     */
    @Min(value = 0, message = "状态值不正确", groups = ValidationGroups.Update.class)
    @Max(value = 1, message = "状态值不正确", groups = ValidationGroups.Update.class)
    private Integer status;
}
