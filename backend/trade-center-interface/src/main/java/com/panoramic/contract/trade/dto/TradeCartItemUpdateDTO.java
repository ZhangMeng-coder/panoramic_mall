package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车行数量请求参数（trade-center 域内部接口与 mall-bff 同源共享）。
 * <p>⚠ <b>语义是整份覆盖</b>（PUT 语义）：域侧无条件把该行 {@code quantity} 写成传入值，
 * 不做「服务端 +1 / -1」。前端数量框每次提交的是**结果值**，故这里没有 delta 字段。</p>
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：值只能由端 BFF 从登录态取，
 * 域侧按它收窄作用域——行 id 是跨顾客可猜的连续值，少一个锚点就是一次越权写。</p>
 */
@Data
public class TradeCartItemUpdateDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;

    /**
     * 新数量：1..999（整份覆盖，不是增量）
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;
}
