package com.panoramic.contract.trade.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单分页查询参数（顾客侧 / 商户侧共用）。
 *
 * <p>⚠ <b>本类里没有任何锚点字段</b>（顾客 id / 店铺 id 都不在）：锚点在**路径**上
 * （顾客侧 {@code /order/customer/{customerId}/page}、商户侧 {@code /order/store/{storeId}/page}），
 * 由端 BFF 从登录态取后填进路径。形态上没有锚点字段，调用方**想传也传不进来**——
 * 「哪一侧能筛什么」由类型说了算，而不是靠约定。平台侧那个能筛锚点的子类见
 * {@link TradeOrderPlatformPageQueryDTO}，两个类是刻意拆的。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeOrderPageQueryDTO extends BasePageVO {

    /**
     * 订单号<b>精确</b>匹配；不填则不筛
     */
    private String orderNo;

    /**
     * 订单状态（枚举名，如 {@code PAID}）；不填则不筛
     *
     * <p>⚠ 与 {@link TradeOrderCreateDTO#getSource()} 同理用 {@code String}：状态枚举在域内
     * （{@code OrderStatus}），契约包不再立第二份。取值由域内解析，写了别的值一律 400。</p>
     */
    private String status;
}
