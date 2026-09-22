package com.panoramic.mallbff.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单请求参数（页面级，{@code POST /orders} 的入参）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.trade.dto.TradeOrderCreateDTO} <b>形状相近但各自独立</b>
 * （BFF 私有类型不外扩、域契约不外漏），且<b>刻意多一个字段、少一份形状</b>：</p>
 * <ul>
 *   <li><b>多 {@code addressId}</b>：页面传的是地址 <b>id</b> 而不是地址快照——快照由本层经
 *       customer-center 取回（顺带校验归属）后组装，{@code trade-center} <b>结构上调不到</b>
 *       customer-center（每个域只依赖自己的 {@code <域>-interface}）。</li>
 *   <li><b>少价格 / 少店铺</b>：店铺由商品归属推出（一单一店，域内按 {@code storeId} 拆单），
 *       价格由服务端从商品域读——「顾客改包就能改价」的字段一律不进 DTO。</li>
 * </ul>
 *
 * <p>⚠ <b>不含 {@code customerId}</b>（cross-cutting 第 22 条）：顾客 id 只能取自登录态
 * （{@code UserContext.getUserId()}），绝不从请求体接收——域内不做任何鉴权，
 * {@code customerId} 就是数据权限本身。</p>
 *
 * <p>⚠ <b>{@code cartItemIds} 是本层自己的字段，不会传给 trade-center</b>：域的单据入参里没有它，
 * 清车是下单成功之后本层的<b>第二步</b>（见 {@code OrderBffService} 与
 * docs/contracts/mall-bff.md「下单后的清车」）。它只在 {@code source=CART}（购物车结算）时非空。</p>
 */
@Data
public class MallOrderCreateDTO {

    /**
     * 订单来源：{@code DIRECT}（详情页直购）/ {@code CART}（购物车结算）
     * <p>⚠ 用 {@code String} 而不是本端枚举：取值枚举在域内（{@code OrderSource}），
     * 在本端另立一个同义枚举就是同一份取值清单写两遍；写了别的值由域内解析时回 400。</p>
     */
    @NotBlank(message = "订单来源不能为空")
    private String source;

    /**
     * 请求级幂等键（客户端每次提交生成一个）——<b>页面契约必填</b>。
     * <p>⚠ 域侧该字段<b>刻意允许为空</b>（为空 = 这次提交不做请求级去重，那是域的能力边界）；
     * 「必填」是页面契约对客户端的要求，故闸门设在这里——在域侧写死会砍掉域原本支持的调用方式，
     * 在本层不设则「必填」两个方向都没落地。命中即原样返回首次那批（不重建、不二次扣库存）。</p>
     */
    @NotBlank(message = "请求编号不能为空")
    private String requestId;

    /**
     * 收货地址 id（<b>必填</b>；本层取回后转成快照传给域）
     * <p>⚠ 传 id 而不是快照：地址属 customer-center，域取不到；归属校验随取地址一并完成
     * （域侧按 {@code id + customerId} 过滤，取不到回 404「地址不存在」，不区分「不存在」与
     * 「不属于本人」——拿别人的 {@code addressId} 下单自然被挡住，且不透出存在性）。</p>
     */
    @NotNull(message = "收货地址不能为空")
    private Long addressId;

    /**
     * 购买商品行（非空；同款多行由域内按 {@code skuId} 合并，本层不必先去重）
     */
    @NotEmpty(message = "下单商品不能为空")
    @Valid
    private List<@NotNull(message = "商品行不能为空") Item> items;

    /**
     * 待清理的<b>购物车行 id</b>（不是 skuId）；仅在 {@code source=CART} 时非空，{@code DIRECT} 直购为空。
     * <p>⚠ 只在<b>下单成功之后</b>用它调 {@code POST /cart/items/remove}——顺序不能反。</p>
     */
    private List<Long> cartItemIds;

    /**
     * 下单商品行（<b>刻意是嵌套类</b>：它不单独出现，只作为 {@link #items} 的元素存在）
     */
    @Data
    public static class Item {

        /**
         * 店铺商品 SKU id（store 域）
         */
        @NotNull(message = "商品规格不能为空")
        private Long skuId;

        /**
         * 购买数量
         * <p>⚠ 上下限（1..999）刻意不在此收口：订单的数量闸门在域内（合并后按聚合的常量校验），
         * 在此再写一遍就是同一事实的第二份。这里的 {@code @NotNull} 只防拆箱 NPE（那是 500，
         * 不是可展示的 400）。</p>
         */
        @NotNull(message = "数量不能为空")
        private Integer quantity;
    }
}
