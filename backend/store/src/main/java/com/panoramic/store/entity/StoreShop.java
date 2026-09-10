package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 店铺实体
 * <p>「账号店同 ID」（一人一店）：店铺主键 id == 店主账号 id（store_user.id），建店时由 store-bff 带入
 * 账号 id（{@code IdType.INPUT}，不再自增），故不再有 owner_user_id 列。审核字段
 * （submit_time/audit_by/audit_time/audit_remark）仅记录留痕，不与平台用户表联查解析姓名。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_shop")
public class StoreShop extends BaseEntity {

    /** 审核状态：草稿 */
    public static final int STATUS_DRAFT = 0;
    /** 审核状态：待审核 */
    public static final int STATUS_PENDING = 1;
    /** 审核状态：已通过 */
    public static final int STATUS_APPROVED = 2;
    /** 审核状态：已驳回 */
    public static final int STATUS_REJECTED = 3;

    /**
     * 主键（== 店主账号 id；IdType.INPUT 由调用方带入）
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 店铺名称
     */
    private String shopName;

    /**
     * 店铺 LOGO 图片 URL
     */
    private String logo;

    /**
     * 店铺简介
     */
    private String intro;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 联系人电话
     */
    private String contactPhone;

    /**
     * 所在地区（省市区）
     */
    private String region;

    /**
     * 详细地址
     */
    private String address;

    /**
     * 营业执照企业名称
     */
    private String licenseName;

    /**
     * 统一社会信用代码
     */
    private String licenseNo;

    /**
     * 营业执照照片 URL
     */
    private String licenseImg;

    /**
     * 审核状态：0草稿，1待审核，2已通过，3已驳回
     */
    private Integer status;

    /**
     * 最近一次提交审核时间
     */
    private LocalDateTime submitTime;

    /**
     * 审核人ID（平台管理员，仅记录）
     */
    private Long auditBy;

    /**
     * 审核时间
     */
    private LocalDateTime auditTime;

    /**
     * 审核备注（驳回原因）
     */
    private String auditRemark;
}
