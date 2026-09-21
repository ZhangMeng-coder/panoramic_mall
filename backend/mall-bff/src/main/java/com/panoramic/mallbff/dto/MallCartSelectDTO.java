package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 购物车选中态请求参数（页面级，{@code PUT /cart/items/{id}/selected} 与 {@code PUT /cart/selected} 共用入参）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.dto.TradeCartSelectDTO} <b>镜像但各自独立</b>。</p>
 *
 * <p>两个端点共用同一入参但是<b>两种作用域</b>：单行版只改该行；不带 id 的那条是
 * <b>域侧整表操作</b>——把该顾客<b>所有</b>行的选中态置为传入值（含已失效的行，域不知道也不判可见性）。
 * 页面上的「全选」勾选态按<b>有效行</b>推导，合计只算「有效且选中」（见 docs/contracts/mall-bff.md「全选作用域」）。</p>
 */
@Data
public class MallCartSelectDTO {

    /**
     * 选中态：true 选中，false 取消
     */
    @NotNull(message = "选中状态不能为空")
    private Boolean selected;
}
