package com.panoramic.common.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 店铺在售商品锁定请求参数（store 域内部接口与 admin BFF 同源共享）
 * <p>仅锁定需要传原因（用于向店主说明锁定理由，店铺端会展示）；解锁无入参。</p>
 */
@Data
public class StoreGoodsLockDTO {

    /**
     * 锁定原因（必填，店铺端「锁定信息」展示）
     */
    @NotBlank(message = "锁定原因不能为空")
    @Size(max = 255, message = "锁定原因不能超过255个字符")
    private String reason;
}
