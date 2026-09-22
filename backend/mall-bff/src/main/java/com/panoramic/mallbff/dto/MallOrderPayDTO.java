package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付请求参数（页面级，{@code POST /orders/{orderNo}/pay} 的入参）。
 *
 * <p>⚠ <b>{@code amount} 必须等于订单总额</b>，但「付对了没有」的校验<b>落在域内</b>
 * （金额是领域规则，只有订单聚合自己比得了封存时的总额）：放到本层或页面层就会出现
 * 「谁都能改一下支付金额」的入口，以及第二份「什么叫付对了」的口径。
 * 故本层只有 {@code @NotNull}——它防的是拆箱 / 空指针变成 500，不是取值是否付对。
 * 不一致时域侧回 400「支付金额与订单总额不一致（应付 X 元，实付 Y 元）」，本层原样透传给页面。</p>
 *
 * <p>⚠ <b>不含 {@code customerId}</b>：锚点取自登录态，且域侧该字段必填——漏传就是
 * 「有权限支付任意一笔单」。</p>
 */
@Data
public class MallOrderPayDTO {

    /**
     * 本次支付金额（元）
     */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;
}
