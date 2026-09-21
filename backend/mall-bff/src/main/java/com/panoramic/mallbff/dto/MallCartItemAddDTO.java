package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求参数（页面级，{@code POST /cart/items} 的入参）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.dto.TradeCartItemAddDTO} <b>字段与约束镜像但各自独立</b>
 * （BFF 私有类型不外扩、域契约不外漏）。约束逐条对齐域侧：BFF 是页面边界，坏输入该在<b>这里</b>回 400，
 * 而不是穿到域里再经熔断语义绕一圈绕回来。</p>
 *
 * <p>⚠ <b>不含 {@code customerId}</b>：顾客 id 只能取自 {@code UserContext}（登录态），
 * 绝不从请求体接收——域内不做任何鉴权，{@code customerId} 就是数据权限本身。</p>
 */
@Data
public class MallCartItemAddDTO {

    /**
     * 店铺商品 SPU id（store 域）
     */
    @NotNull(message = "商品不能为空")
    private Long spuId;

    /**
     * 店铺商品 SKU id（store 域）
     */
    @NotNull(message = "规格不能为空")
    private Long skuId;

    /**
     * 数量（1..999，上限与域侧一致：单行 999）
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;
}
