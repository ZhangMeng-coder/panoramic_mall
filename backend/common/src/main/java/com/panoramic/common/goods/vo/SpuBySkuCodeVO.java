package com.panoramic.common.goods.vo;

import lombok.Data;

/**
 * 按 SKU 编码反查标准商品的结果（店铺端「填 SKU_CODE 预填新增表单」用）。
 * <p>中台 goods_sku.sku_code 无唯一索引，编码可能重复：命中多条时按 SKU id 升序取首条，
 * 并以 {@link #matchedSkuCount} &gt; 1 提示调用方「已取第一条」。</p>
 */
@Data
public class SpuBySkuCodeVO {

    /**
     * 命中的标准商品详情；<b>未命中为 null</b>（不抛异常——店铺端允许「查不到照样自建」）
     */
    private SpuDetailVO spu;

    /**
     * 命中的 SKU 条数：0 = 未命中，&gt;1 = 中台存在重复编码
     */
    private long matchedSkuCount;
}
