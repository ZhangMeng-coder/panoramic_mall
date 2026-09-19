package com.panoramic.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址实体（一个顾客多条，主键自增）。
 * <p>⚠ 主键 {@code IdType.AUTO}：{@code customer_address.id} 是 {@code AUTO_INCREMENT}，由 DB 生成后回填
 * （与 {@link CustomerProfile} 的 {@code IdType.INPUT} 刻意不同——资料一对一、主键即账号 id；地址一对多、无天然业务主键）。</p>
 * <p>归属锚点 {@code customerId} = {@code mall_user.id}（跨域 id 引用、无外键）：本域所有读写一律以
 * 「id + customerId」双条件限定作用域，域内不做身份判断（防线在 mall-bff，见 docs/contracts/customer-center.md）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_address")
public class CustomerAddress extends BaseEntity {

    /**
     * 主键（IdType.AUTO：由 DB 自增生成）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属顾客（= mall_user.id，数据权限锚点）
     */
    private Long customerId;

    /**
     * 收件人姓名
     */
    private String receiverName;

    /**
     * 收件人手机号
     */
    private String receiverPhone;

    /**
     * 省市区（自由文本单列，不建地区表，与 store_shop.region 同口径）
     */
    private String region;

    /**
     * 详细地址
     */
    private String detailAddress;

    /**
     * 默认地址：0否，1是（同一顾客至多一条为 1，由应用层事务保证，见 {@code setDefaultAddress}）
     */
    private Integer isDefault;
}
