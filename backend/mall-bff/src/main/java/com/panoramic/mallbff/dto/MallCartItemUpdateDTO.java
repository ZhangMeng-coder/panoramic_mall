package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 修改购物车行数量请求参数（页面级，{@code PUT /cart/items/{id}} 的入参）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO} <b>镜像但各自独立</b>。</p>
 *
 * <p>⚠ <b>落的是绝对数量，不是增量</b>：数量保存在服务端，页面改一次提交一次；
 * 域侧用一条 UPDATE 直接置值（不是读-改-写，故并发下不会丢更新）。</p>
 *
 * <p>⚠ 库存<b>不在这里判</b>：购物车是购买意向不是占位，加购与改数量都不校验库存
 * （见 docs/contracts/mall-bff.md「加购不校验库存」）。买不买得到以结算时的库存为准，
 * 页面上的「库存不足」只是展示层的 `purchasable` 标记。</p>
 */
@Data
public class MallCartItemUpdateDTO {

    /**
     * 数量（1..999）
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;
}
