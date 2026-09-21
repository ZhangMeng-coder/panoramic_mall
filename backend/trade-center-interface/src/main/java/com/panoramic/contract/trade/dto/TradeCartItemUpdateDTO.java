package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车行数量请求参数（trade-center 域内部接口与 mall-bff 同源共享）。
 * <p>⚠ <b>语义是整份覆盖</b>（PUT 语义）：域侧无条件把该行 {@code quantity} 写成传入值，
 * 不做「服务端 +1 / -1」。前端数量框每次提交的是**结果值**，故这里没有 delta 字段。</p>
 */
@Data
public class TradeCartItemUpdateDTO {

    /**
     * 新数量：1..999（整份覆盖，不是增量）
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;
}
