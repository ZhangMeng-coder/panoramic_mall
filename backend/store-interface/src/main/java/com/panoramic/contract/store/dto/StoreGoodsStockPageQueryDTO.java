package com.panoramic.contract.store.dto;

import com.panoramic.common.vo.BasePageVO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SKU 库存分页查询参数（作用域内的商品，store 域内部接口与 store-bff 同源共享）。
 * <p>库存页按 SKU 平铺一行一条，作用域限定在登录店主名下（{@code storeId} 由调用方 BFF
 * 从登录态带入本 DTO，cross-cutting 第 22 条）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsStockPageQueryDTO extends BasePageVO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id）。
     * <p>⚠ 值由端 BFF 自登录态取（{@code LoginUser.getId()}）并**无条件覆盖**，页面不得提供；
     * 只在域入口必填（{@link StoreScopeGroup}），见 cross-cutting 第 22 条。</p>
     */
    @NotNull(message = "店铺ID不能为空", groups = StoreScopeGroup.class)
    private Long storeId;

    /**
     * 关键字（模糊匹配 SKU 编码 或 商品名称）
     */
    private String keyword;

    /**
     * 上下架筛选（0 下架 / 1 上架），空为全部
     */
    private Integer shelfStatus;

    /**
     * 仅看低库存（{@code stock <= warn_stock}）；空 / false 为全部。
     * <p>未设预警阈值（{@code warn_stock} 为 NULL）的行天然不满足该条件，不会被选中。</p>
     */
    private Boolean lowStockOnly;
}
