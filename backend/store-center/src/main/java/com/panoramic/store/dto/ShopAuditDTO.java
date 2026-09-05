package com.panoramic.store.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 店铺审核请求参数
 */
@Data
public class ShopAuditDTO {

    /**
     * 审核结果：true=通过，false=驳回
     */
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    /**
     * 审核备注（驳回时必填原因）
     */
    @Size(max = 255, message = "审核备注不能超过255个字符")
    private String auditRemark;
}
