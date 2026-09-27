package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 本店商品与库存规模查询参数（store 域内部接口与 store-bff 同源共享）。
 *
 * <p><b>本店快照</b>：上架 / 下架商品数（按 SPU）与库存异常数（按 SKU 行）三个计数，
 * 作用域限定在登录店主名下（{@code storeId} 由调用方 BFF 从登录态带入本 DTO，cross-cutting 第 22 条）。</p>
 *
 * <p>⚠ <b>无时间范围字段，也不是「还没加」</b>：三个数都回答「本店现在什么样」，
 * 没有「本月的上架数」这种语义——别为了和统计接口对称塞 {@code start} / {@code end}
 * （见 {@link com.panoramic.contract.store.vo.StoreGoodsStatsVO} 与 cross-cutting 第 25 条）。</p>
 *
 * <p>⚠ <b>必填锚点挂在默认组</b>（不是 {@link StoreScopeGroup}）：本 DTO <b>不是任何端的页面入参类型</b>
 * ——店主端看板的页面入参是 store-bff 自有的窗口 DTO，作用域由该层从登录态取。
 * 与评价两条写（{@code StoreGoodsEvaluationSubmitDTO} / {@code StoreGoodsEvaluationReplyDTO}）同档，
 * 判据见 {@code docs/contracts/store.md} 第三节「必填校验分两档」。</p>
 */
@Data
public class StoreGoodsStatsQueryDTO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id）。
     * <p>⚠ 值由端 BFF 自登录态取（{@code LoginUser.getId()}）并**无条件覆盖**，页面不得提供；
     * <b>无全量视角</b>——「全平台的上下架数」不是本能力的语义（平台视角要的是店铺数与商品总数，
     * 那是 {@code getShopStats}），故此处必填。</p>
     */
    @NotNull(message = "店铺ID不能为空")
    private Long storeId;
}
