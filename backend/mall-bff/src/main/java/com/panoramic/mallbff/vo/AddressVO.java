package com.panoramic.mallbff.vo;

import lombok.Data;

/**
 * 收货地址响应（<b>本端私有类型</b>，{@code GET /addresses} 的出参元素）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.customer.vo.CustomerAddressVO} <b>字段一一对应但各自独立</b>：
 * 域契约类只许在 BFF 与域之间流动，<b>不外扩到页面</b>；页面级类型只许在本端，<b>不外扩到域</b>。
 * 两者刻意不共用一份，改动其一时以「页面契约」为准（docs/contracts/mall-bff.md）。</p>
 *
 * <p>{@code isDefault} 是<b>只读</b>标记（0否 / 1是）：设默认只有
 * {@code POST /addresses/{id}/default} 一条路径（首条新增由域侧自动置默认），
 * 故它不出现在 {@link com.panoramic.mallbff.dto.AddressSaveDTO} 里。</p>
 */
@Data
public class AddressVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 收件人姓名
     */
    private String receiverName;

    /**
     * 收件人手机号
     */
    private String receiverPhone;

    /**
     * 省市区（自由文本单列，如「广东省 深圳市 南山区」）
     */
    private String region;

    /**
     * 详细地址
     */
    private String detailAddress;

    /**
     * 默认地址：0否，1是
     */
    private Integer isDefault;
}
