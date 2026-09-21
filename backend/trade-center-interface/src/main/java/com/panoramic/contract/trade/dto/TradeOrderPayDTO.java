package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付请求参数（顾客侧假支付）。
 *
 * <p>⚠ <b>{@code amount} 必须等于订单总额</b>，且这道校验<b>落在域内</b>（金额是领域规则）：
 * 订单总额是封存（seal）时冻结在聚合里的值，只有聚合自己比得了；放到本层或页面层就会出现
 * 「谁都能改一下支付金额」的入口，以及第二份「什么叫付对了」的口径。不一致回 400。
 * 故本类只有 {@code @NotNull}——取值是否付对不在这里判。</p>
 */
@Data
public class TradeOrderPayDTO {

    /**
     * 本次支付金额（元）
     *
     * <p>⚠ {@code @NotNull} 防的是拆箱 / 空指针变成 500；「付对了没有」由域内比对（见类注释）。</p>
     */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;
}
