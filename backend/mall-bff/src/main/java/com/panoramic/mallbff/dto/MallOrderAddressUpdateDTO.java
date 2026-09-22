package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改订单收货地址的页面入参（{@code PUT /orders/{orderNo}/address}）。
 *
 * <p>⚠ <b>只传 {@code addressId}</b>，不传地址字段：地址由本层用它与下单**同一个**取地址路径
 * （{@code OrderBffService#addressSnapshot}）取回并校验归属，再组快照传域——页面无权自造地址内容，
 * 否则顾客能把订单改成任意地址、连地址簿都不沾。</p>
 *
 * <p>⚠ <b>顾客 id 不在这里</b>：它只能取自登录态（{@code UserContext}），
 * 绝不从请求体接收——域内不做任何鉴权，那个 id 就是数据权限本身。</p>
 */
@Data
public class MallOrderAddressUpdateDTO {

    /**
     * 新的收货地址 id（**必须属于当前顾客**；不属本人 → 域侧 404「地址不存在」，不区分两种情形）
     */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;
}
