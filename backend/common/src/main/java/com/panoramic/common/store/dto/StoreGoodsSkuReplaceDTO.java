package com.panoramic.common.store.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

/**
 * 店铺商品 SKU 整单替换请求参数（新增/修改 SKU 统一走此接口）。
 * <p>空列表 = 清空全部 SKU；已上架的 SKU 必须原样保留且不得缺失，由 store 域校验（未上架才可增/改/删）。</p>
 */
@Data
public class StoreGoodsSkuReplaceDTO {

    /**
     * 替换后的完整 SKU 列表
     */
    @Valid
    private List<StoreGoodsSkuDTO> skus;
}
