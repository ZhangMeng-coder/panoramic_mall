package com.panoramic.common.goods.dto;

import lombok.Data;

import java.util.List;

/**
 * 全量替换商品 SKU 请求参数（规格管理弹窗保存用）
 */
@Data
public class SpuSkuReplaceDTO {

    /**
     * SKU 列表（允许为空：空即清空该 SPU 全部 SKU）。
     * 结构校验与对商品规格属性配置的匹配校验均由服务层完成
     */
    private List<SkuDTO> skus;
}
