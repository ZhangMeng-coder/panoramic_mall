package com.panoramic.common.store.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺响应（店主「我的店铺」与平台「店铺列表/详情」共用，store 域内部接口与各端 BFF 同源共享）。
 * <p>「账号店同 ID」（一人一店）：店铺主键 id == 店主账号 id，故本 VO 不携带 ownerUserId/ownerUsername
 * （admin 平台侧不读店主账号，D6）；店铺资料/审核留痕字段保留。</p>
 */
@Data
public class ShopVO {

    /**
     * 主键（== 店主账号 id）
     */
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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
