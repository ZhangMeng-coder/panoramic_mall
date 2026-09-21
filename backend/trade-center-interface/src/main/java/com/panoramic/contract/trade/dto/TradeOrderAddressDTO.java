package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 收货地址快照（trade-center 域内部接口与端 BFF 同源共享）：下单时作为
 * {@link TradeOrderCreateDTO#getAddress()} 传进来，读订单时又作为 {@code TradeOrderVO#getAddress()} 传出去
 * ——**同一份形状在两侧共用**，故只有一个类。
 *
 * <p>⚠ <b>为什么下单传快照而不是 {@code addressId}</b>：地址属于 customer-center（顾客域），
 * 而每个域只依赖自己的 {@code <域>-interface}——trade-center <b>结构上拿不到</b>顾客地址。
 * 取地址与归属校验由 mall-bff 做完再传快照（见 {@code docs/contracts/trade-center.md} 第三节）。
 * 快照是<b>下单当时</b>的地址：此后顾客改地址 / 删地址都不影响已下的单。</p>
 *
 * <p>⚠ <b>长度上限刻意不在这里</b>（本类没有 {@code @Size}）：唯一一份在域内 {@code OrderAddress}
 * 的四个 {@code MAX_*_LENGTH}（它们与 {@code trade_order} 的四列同口径）。在此再写一遍
 * 就是同一事实的第二份表述——改了列宽只会漂移其中一份。本类只拦「结构性缺失」（null / 空白），
 * 好让它在进域之前就带字段名报错；取值是否合法一律由域内判，错误同样是 400。</p>
 */
@Data
public class TradeOrderAddressDTO {

    /**
     * 收件人（取值上限见域内 {@code OrderAddress.MAX_NAME_LENGTH}）
     */
    @NotBlank(message = "收件人不能为空")
    private String receiverName;

    /**
     * 收件人电话（取值上限见域内 {@code OrderAddress.MAX_PHONE_LENGTH}）
     */
    @NotBlank(message = "收件人电话不能为空")
    private String receiverPhone;

    /**
     * 省市区（由 mall-bff 从顾客地址拼好传来，域内不解析行政区划）
     */
    @NotBlank(message = "所在地区不能为空")
    private String region;

    /**
     * 详细地址（取值上限见域内 {@code OrderAddress.MAX_DETAIL_LENGTH}）
     */
    @NotBlank(message = "详细地址不能为空")
    private String detail;
}
