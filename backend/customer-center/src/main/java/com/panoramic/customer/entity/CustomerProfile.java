package com.panoramic.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 顾客资料实体（一对一：主键 = 顾客账号 id）。
 * <p>⚠ 主键 {@code IdType.INPUT} 是<b>刻意</b>的，不要「顺手」改成 {@code AUTO}：资料行与顾客账号
 * {@code mall_user} 一对一，主键就是账号 id（照 store 域 {@code store_shop}「账号店同 ID」手法），
 * 由调用方带入、建行时显式写入，不依赖 DB 自增（{@code customer_profile.id} 也无 AUTO_INCREMENT）。</p>
 * <p>本域<b>不持顾客账号</b>（手机号/密码在 mall-bff 的 {@code mall_user}），故本表不含账号字段、不与之联查。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_profile")
public class CustomerProfile extends BaseEntity {

    /**
     * 主键（== mall_user.id；IdType.INPUT 由调用方带入，非自增）
     */
    @TableId(type = IdType.INPUT)
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
