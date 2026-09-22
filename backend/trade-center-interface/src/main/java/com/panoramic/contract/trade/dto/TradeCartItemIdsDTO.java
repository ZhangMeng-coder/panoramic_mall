package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量删除购物车行请求参数（trade-center 域内部接口与 mall-bff 同源共享）。
 * <p>装的是<b>购物车行 id</b>（{@code trade_cart_item.id}），<b>不是 skuId</b>：
 * 删除的作用域必须落在「顾客自己名下的行」上，行 id + 锚点 {@code customerId} 两条件即可限定；
 * 若改成按 skuId 删，域侧还要反查行（且 skuId 跨顾客可重复，多了一层说错就删错的机会）。</p>
 * <p>⚠ {@code @NotEmpty}：空列表在域侧会被直接短路返回（不拼 {@code IN ()}），
 * 但契约上仍要求调用方别传空——「什么都没选就点删除」应当是前端拦住，不是靠后端静默成功。</p>
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：值只能由端 BFF 从登录态取，
 * 域侧按它收窄——只按 ids 删会把「行 id 猜对」直接变成「删掉别人的行」。</p>
 */
@Data
public class TradeCartItemIdsDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;

    /**
     * 待删除的购物车行 id 列表（非空）
     */
    @NotEmpty(message = "请选择要删除的商品")
    private List<Long> ids;
}
