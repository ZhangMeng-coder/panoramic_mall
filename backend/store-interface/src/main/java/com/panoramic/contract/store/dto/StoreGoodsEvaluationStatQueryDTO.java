package com.panoramic.contract.store.dto;

import lombok.Data;

/**
 * 评价星级分布查询参数（store 域内部接口与端 BFF 同源共享）。
 * <p><b>跨店通用</b>：与 {@code StoreGoodsEvaluationPageQueryDTO} 同一组可空条件——
 * C 端传 {@code spuId} 看「该商品各星级各有多少人」，商户端传 {@code storeId} 看「本店整体分布」。</p>
 * <p>⚠ 无集合字段，故走 {@code GET + @SpringQueryMap}（不必像分页那样为绕开集合序列化而用 POST）。</p>
 */
@Data
public class StoreGoodsEvaluationStatQueryDTO {

    /**
     * 商品 SPU id；空 = 不限定商品
     */
    private Long spuId;

    /**
     * 作用域：所属店铺 id（= 店主账号 id）；空 = 不限定店铺
     */
    private Long storeId;
}
