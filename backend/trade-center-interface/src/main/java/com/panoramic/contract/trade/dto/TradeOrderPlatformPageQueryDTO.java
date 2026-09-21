package com.panoramic.contract.trade.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 平台侧订单分页查询参数：在 {@link TradeOrderPageQueryDTO} 之上补 {@code storeId} / {@code customerId} 两个筛选。
 *
 * <p>⚠ <b>为什么是「继承出一个子类」而不是「父类上加两个可选锚点字段」</b>：管理端本就是全量视角、
 * 没有锚点约束，故它可以按店铺 / 按顾客筛；而顾客侧与商户侧**只该看自己那一份**，多出可选锚点字段后
 * 「顾客侧传了 storeId」「商户侧漏传 customerId」这类**形态上本不该成立**的调用就顺理成章地出现了。
 * 拆成两个类之后，「哪些侧能筛锚点」在类型上就成立（域侧的端口用同样的手法拆，见域内
 * {@code PlatformOrderQuery}）。</p>
 *
 * <p>⚠ 这里筛的是**他自己选的**店铺 / 顾客，不是「调用方的身份」——平台侧的调用方是管理端，
 * 它的身份不落在这两个字段上。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeOrderPlatformPageQueryDTO extends TradeOrderPageQueryDTO {

    /**
     * 按店铺筛；不填则不筛
     */
    private Long storeId;

    /**
     * 按顾客筛；不填则不筛
     */
    private Long customerId;
}
