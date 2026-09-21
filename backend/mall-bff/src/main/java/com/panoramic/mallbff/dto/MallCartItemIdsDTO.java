package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除购物车行请求参数（页面级，{@code POST /cart/items/remove} 的入参）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.dto.TradeCartItemIdsDTO} <b>镜像但各自独立</b>。</p>
 *
 * <p>⚠ <b>为什么是 {@code POST} + body，而不是 {@code DELETE} 带 body 或逐个删</b>：
 * 集合入参走 body 规避 {@code @SpringQueryMap} 的集合序列化问题，与 store 域跨店通用侧的
 * 批量口径同形（见 docs/contracts/cross-cutting.md 第 18 条）；逐个删则是「选中 N 行就发 N 个请求」。</p>
 *
 * <p>⚠ 域侧对「行不存在 / 不属于本人」是<b>幂等 no-op</b>：多选删除不该因某一行被并发删掉而整体失败。</p>
 */
@Data
public class MallCartItemIdsDTO {

    /**
     * 待删除的购物车行 id（本顾客自己的行）
     */
    @NotEmpty(message = "请选择要删除的商品")
    private List<Long> ids;
}
