package com.panoramic.contract.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改订单收货地址的请求参数（**仅待支付可改**，闸门在域内）。
 *
 * <p>改的是**这一笔订单的地址快照**（覆盖 `trade_order` 的四个收货地址列），
 * **不动顾客的地址簿**，也不回溯影响别的订单——「一笔订单寄到哪儿」是这笔订单自己的事实。</p>
 *
 * <p>⚠ <b>入参仍是快照（{@link TradeOrderAddressDTO}），不是 {@code addressId}</b>：
 * 与 {@link TradeOrderCreateDTO#getAddress()} 同一个理由——地址属于 customer-center，
 * 而 trade-center 结构上调不到顾客地址。端 BFF 用 {@code addressId} 取回地址、校验归属，
 * 再把快照传进来（见 {@code docs/contracts/trade-center.md} 第三节）。</p>
 *
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：写操作的作用域**没有**
 * 「合法全量视角」，省掉它就是「有权限改任意一笔单的收货地址」。值只能由端 BFF 从登录态取，
 * 域侧按它收窄到「本人的那一笔」。</p>
 */
@Data
public class TradeOrderAddressUpdateDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;

    /**
     * 新的收货地址快照（**必填**；长度与空白校验在域内 {@code OrderAddress} 的构造器里）
     *
     * <p>⚠ {@code @Valid} 的级联**跳过 null 元素**，故 {@code @NotNull} 不能省：
     * 没有它，{@code address: null} 会一路传到域内并以 NPE / 500 出去，而不是 400。</p>
     */
    @NotNull(message = "收货地址不能为空")
    @Valid
    private TradeOrderAddressDTO address;
}
