package com.panoramic.contract.customer.vo;

import lombok.Data;

/**
 * 收货地址响应（customer-center 域内部接口与 mall-bff 同源共享）。
 * <p>字段即 {@code CustomerAddressSaveDTO} + 主键 {@code id} + {@code isDefault}
 * （默认标记只读、只能经 {@code setDefaultAddress} 变更，故落库 DTO 里没有它）。</p>
 */
@Data
public class CustomerAddressVO {

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
     * 默认地址：0否，1是（同一顾客至多一条为 1，由应用层事务保证）
     */
    private Integer isDefault;
}
