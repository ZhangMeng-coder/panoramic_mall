package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求参数（trade-center 域内部接口与 mall-bff 同源共享）。
 * <p>⚠ <b>数量上限 999 在此收口</b>（{@code @Max}）：这是新行插入路径的唯一一道闸。既有行的<b>累加</b>
 * 路径不经过本 DTO 的校验（数量由域内的条件原子自增 SQL 用 {@code LEAST(quantity + delta, 999)} 封顶），
 * 两条路径都必须封顶——只封一边的话，反复加购同一 SKU 就能把 quantity 顶到 INT 上限。</p>
 * <p>⚠ 数量下界同样要收口（{@code @Min(1)}）：DB 列是 {@code INT NOT NULL}，{@code 0} 会静默入库成
 * 「加购了一行但数量为 0」，前端拿到一个已知分支之外的态。</p>
 * <p>⚠ <b>数据权限锚点 {@code customerId} 在本 DTO 里、且必填</b>（cross-cutting 第 22 条）：锚点不进路径段，
 * 只作为入参 DTO 的字段。值<b>只能由端 BFF 从登录态取</b>（{@code LoginUser.getId()}），**禁止**从前端入参透传
 * ——前端传来的 id 一旦被当作锚点，等于把数据权限交给页面。域侧只做「传了就按它筛」，不判身份。</p>
 */
@Data
public class TradeCartItemAddDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取，不透传前端入参）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;

    /**
     * 店铺商品 SPU id（store 域；域内不校验其存在性——购物车只记引用，可见性由 mall-bff 读时重判）
     */
    @NotNull(message = "商品不能为空")
    private Long spuId;

    /**
     * 店铺商品 SKU id（store 域；与 {@code spuId} 一起构成购物车行的商品引用）
     */
    @NotNull(message = "商品规格不能为空")
    private Long skuId;

    /**
     * 加购数量：1..999
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    @Max(value = 999, message = "数量不能超过999")
    private Integer quantity;
}
