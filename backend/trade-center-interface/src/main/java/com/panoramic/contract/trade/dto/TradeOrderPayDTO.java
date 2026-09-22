package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付请求参数（假支付）。
 *
 * <p>⚠ <b>{@code amount} 必须等于订单总额</b>，且这道校验<b>落在域内</b>（金额是领域规则）：
 * 订单总额是封存（seal）时冻结在聚合里的值，只有聚合自己比得了；放到本层或页面层就会出现
 * 「谁都能改一下支付金额」的入口，以及第二份「什么叫付对了」的口径。不一致回 400。
 * 故本类只有 {@code @NotNull}——取值是否付对不在这里判。</p>
 *
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：写操作的作用域<b>没有</b>「合法全量视角」，
 * 省掉它就是「有权限改任意一笔单」。值只能由端 BFF 从登录态取，域侧按它收窄到「本人的那一笔」。</p>
 */
@Data
public class TradeOrderPayDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;

    /**
     * 本次支付金额（元）
     *
     * <p>⚠ {@code @NotNull} 防的是拆箱 / 空指针变成 500；「付对了没有」由域内比对（见类注释）。</p>
     */
    @NotNull(message = "支付金额不能为空")
    private BigDecimal amount;
}
