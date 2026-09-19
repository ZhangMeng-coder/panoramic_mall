package com.panoramic.contract.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收货地址保存/编辑请求参数（customer-center 域内部接口与 mall-bff 同源共享）。
 * <p>⚠ <b>刻意不含 {@code isDefault}</b>：设默认只有两条路径——「首条地址自动设为默认」与
 * {@code setDefaultAddress}。若新增/编辑也能直接传 {@code isDefault}，设默认就有了三条路径，
 * 其中两条会绕过「同一顾客至多一条默认地址」的事务清位逻辑，默认地址唯一性必然被破坏。</p>
 */
@Data
public class CustomerAddressSaveDTO {

    /**
     * 收件人姓名
     */
    @NotBlank(message = "收件人姓名不能为空")
    @Size(max = 50, message = "收件人姓名不能超过50个字符")
    private String receiverName;

    /**
     * 收件人手机号
     */
    @NotBlank(message = "收件人手机号不能为空")
    @Size(max = 20, message = "收件人手机号不能超过20个字符")
    private String receiverPhone;

    /**
     * 省市区（自由文本单列，不建地区表，与 store_shop.region 同口径，如「广东省 深圳市 南山区」）
     */
    @Size(max = 100, message = "省市区不能超过100个字符")
    private String region;

    /**
     * 详细地址
     */
    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String detailAddress;
}
