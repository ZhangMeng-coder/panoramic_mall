package com.panoramic.contract.customer.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 顾客资料保存请求参数（customer-center 域内部接口与 mall-bff 同源共享）。
 * <p>全字段选填：资料页保存时不传的字段即不覆盖（域侧按「非 null 才更新」处理），
 * 故本 DTO 不带 {@code @NotNull} / {@code @NotBlank}。</p>
 */
@Data
public class CustomerProfileSaveDTO {

    /**
     * 昵称
     */
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    /**
     * 头像 URL
     */
    @Size(max = 255, message = "头像地址长度不能超过255个字符")
    private String avatar;

    /**
     * 性别：0未知，1男，2女
     */
    private Integer gender;

    /**
     * 生日
     */
    private LocalDate birthday;
}
