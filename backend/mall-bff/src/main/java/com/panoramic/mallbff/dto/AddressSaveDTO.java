package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 收货地址保存 / 编辑请求参数（页面级，{@code POST /addresses} 与 {@code PUT /addresses/{id}} 共用入参）。
 *
 * <p>⚠ <b>刻意不含 {@code isDefault}</b>：设默认只有两条路径——「首条地址自动设为默认」与
 * {@code POST /addresses/{id}/default}。入参若也能传默认位，设默认就多出一条绕过默认唯一性维护的路径。</p>
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.customer.dto.CustomerAddressSaveDTO} <b>字段与约束镜像但各自独立</b>
 * （BFF 私有类型不外扩、域契约不外漏）。约束<b>逐条对齐域侧</b>：BFF 是页面边界，坏输入该在<b>这里</b>回 400，
 * 而不是穿到域里再经熔断语义绕一圈绕回来。</p>
 */
@Data
public class AddressSaveDTO {

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
     * 省市区（自由文本单列，不建地区表，如「广东省 深圳市 南山区」）
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
