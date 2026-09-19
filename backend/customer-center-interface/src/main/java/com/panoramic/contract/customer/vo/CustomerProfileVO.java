package com.panoramic.contract.customer.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 顾客资料响应（customer-center 域内部接口与 mall-bff 同源共享）。
 * <p>主键 id == {@code mall_user.id}（一对一，域内 IdType.INPUT 显式插入，照 store_shop「账号店同 ID」手法）；
 * 不含账号字段（手机号等账号信息归 mall-bff 的 {@code mall_user}，域内不持、不联查）。</p>
 */
@Data
public class CustomerProfileVO {

    /**
     * 主键（== mall_user.id）
     */
    private Long id;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像 URL
     */
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
