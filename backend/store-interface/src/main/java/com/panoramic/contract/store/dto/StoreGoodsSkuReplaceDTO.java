package com.panoramic.contract.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 店铺商品 SKU 整单替换请求参数（新增/修改 SKU 统一走此接口）。
 * <p>空列表 = 清空全部 SKU；已上架的 SKU 必须原样保留且不得缺失，由 store 域校验（未上架才可增/改/删）。</p>
 */
@Data
public class StoreGoodsSkuReplaceDTO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id），以「SPU id + store_id」双条件限定作用对象。
     * <p>⚠ 值由端 BFF 自登录态取（{@code LoginUser.getId()}）并**无条件覆盖**，页面不得提供；
     * 只在域入口必填（{@link StoreScopeGroup}），见 cross-cutting 第 22 条。</p>
     */
    @NotNull(message = "店铺ID不能为空", groups = StoreScopeGroup.class)
    private Long storeId;

    /**
     * 替换后的完整 SKU 列表
     */
    @Valid
    private List<StoreGoodsSkuDTO> skus;
}
