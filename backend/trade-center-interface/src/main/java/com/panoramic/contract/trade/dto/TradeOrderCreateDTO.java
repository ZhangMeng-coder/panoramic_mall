package com.panoramic.contract.trade.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单请求参数（trade-center 域内部接口与端 BFF 同源共享）。
 *
 * <p>⚠ <b>入参里没有店铺、没有价格、没有金额</b>：店铺由商品归属推出（一单一店，域内按 {@code storeId} 拆单），
 * 价格必须由服务端从商品域读——「顾客改包就能改价」的字段一律不进 DTO。这里只保留
 * 「谁在买、从哪来、寄到哪儿、买什么、买几件、以及一个幂等键」。
 * <b>顾客 id 在、且必填</b>：它是数据权限锚点，锚点不进路径段（cross-cutting 第 22 条），
 * 值只能由端 BFF 从登录态取（{@code LoginUser.getId()}），**禁止**从前端入参透传。</p>
 *
 * <p>⚠ 一次提交按 {@code storeId} <b>拆成多笔</b>（一单一店），故出参是 {@code List<TradeOrderVO>}，
 * 顺序 = {@code storeId} 升序（确定）。</p>
 */
@Data
public class TradeOrderCreateDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取，不透传前端入参）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;

    /**
     * 订单来源：{@code DIRECT}（详情页直购）/ {@code CART}（购物车结算）。
     *
     * <p>⚠ 类型是 {@code String} 而不是枚举：那个枚举在域内（{@code OrderSource}），
     * 本模块看不到它。**不要再在契约包另立一个同义枚举**——同一份取值清单写两遍就是两个会漂移的地方。
     * 取值由域内解析，写了别的值一律 400（提示语由域枚举拼出，故也不会漂移）。</p>
     */
    @NotBlank(message = "订单来源不能为空")
    private String source;

    /**
     * 请求级幂等键（客户端每次提交生成一个；命中即原样返回首次那批，不重建、不二次扣库存）。
     *
     * <p>⚠ 此处**不设 {@code @NotBlank}**，因为域侧刻意允许为空——为空表示「这次提交不做请求级去重」
     * （见域内 {@code OrderRepository#occupy}）。「必填」是<b>页面契约</b>对客户端的要求
     * （mall-bff 必传），不是域的能力边界；把它写死在这里，等于顺手砍掉域原本支持的调用方式。</p>
     */
    private String requestId;

    /**
     * 收货地址快照（**必填**，不是 {@code addressId}——理由见 {@link TradeOrderAddressDTO}）
     */
    @NotNull(message = "收货地址不能为空")
    @Valid
    private TradeOrderAddressDTO address;

    /**
     * 购买商品行（**必填**；同款多行由域内按 {@code skuId} 合并，调用方不必先去重）
     *
     * <p>⚠ 元素上的 {@code @NotNull} 不能省：{@code @Valid} 的级联**跳过 null 元素**，
     * 一个 {@code {items:[null]}} 的 body 能穿过列表级 {@code @NotEmpty}，随后在应用层
     * 解引用时炸成 NPE → 500。写在元素类型上才是 400（与 {@link Item#getQuantity()} 同一理由：
     * 结构性的缺失要在进域之前报出来）。</p>
     */
    @NotEmpty(message = "下单商品不能为空")
    @Valid
    private List<@NotNull(message = "商品行不能为空") Item> items;

    /**
     * 下单商品行（**刻意是嵌套类**：契约表第三节的 dto 清单里没有独立的行类型——它不单独出现，
     * 只作为 {@link #items} 的元素存在）
     */
    @Data
    public static class Item {

        /**
         * 店铺商品 SKU id（store 域）
         */
        @NotNull(message = "商品规格不能为空")
        private Long skuId;

        /**
         * 购买数量：1..999
         *
         * <p>⚠ <b>上下限刻意不在此收口</b>：订单的数量闸门在域内（{@code OrderCreateCommand} 合并后按
         * {@code OrderItem.MIN_QUANTITY/MAX_QUANTITY} 校验），在此再写一遍就是同一事实的第二份。
         * 这一点与购物车的 {@code TradeCartItemAddDTO} 不同——那边域内没有这道校验（新增行靠 DTO、
         * 累加路径靠 SQL 封顶），故上限只能写在 DTO 上；**两边形状不同是因为闸门位置不同，不是因为口径不同**。
         * 这里的 {@code @NotNull} 只防一件事：拆箱成 {@code int} 时 NPE（那会变成 500，而不是可展示的 400）。</p>
         */
        @NotNull(message = "数量不能为空")
        private Integer quantity;
    }
}
