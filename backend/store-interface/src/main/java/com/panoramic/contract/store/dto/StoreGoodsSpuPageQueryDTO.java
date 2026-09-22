package com.panoramic.contract.store.dto;

import com.panoramic.common.vo.BasePageVO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺在售商品分页查询参数（店主自己的商品，store 域内部接口与 store-bff 同源共享）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsSpuPageQueryDTO extends BasePageVO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id）。
     * <p>⚠ 值由端 BFF 自登录态取（{@code LoginUser.getId()}）并**无条件覆盖**，页面不得提供；
     * 只在域入口必填（{@link StoreScopeGroup}），见 cross-cutting 第 22 条。
     * 本能力无「全量视角」（管理端跨店分页走 {@code pageStoreGoodsCrossShop}），故必填。</p>
     */
    @NotNull(message = "店铺ID不能为空", groups = StoreScopeGroup.class)
    private Long storeId;

    /**
     * 关键字（模糊匹配商品名称）
     */
    private String keyword;

    /**
     * 分类筛选（引用中台分类 id），空为全部
     */
    private Long categoryId;

    /**
     * 品牌筛选（引用中台品牌 id），空为全部
     */
    private Long brandId;

    /**
     * 上下架筛选（0 下架 / 1 上架），空为全部
     */
    private Integer shelfStatus;
}
