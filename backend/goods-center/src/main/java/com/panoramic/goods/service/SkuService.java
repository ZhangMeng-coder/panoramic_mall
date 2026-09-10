package com.panoramic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.goods.entity.GoodsSku;

import java.util.List;

/**
 * 商品 SKU 服务（SPU 的子聚合数据，逻辑删除）
 */
public interface SkuService extends IService<GoodsSku> {

    /**
     * 查询某商品的全部 SKU（按 id 升序）
     *
     * @param spuId 商品ID
     * @return SKU 列表
     */
    List<GoodsSku> listBySpuId(Long spuId);

    /**
     * 级联逻辑删除某商品的全部 SKU（删除商品用）
     *
     * @param spuId 商品ID
     */
    void removeBySpuId(Long spuId);

    /**
     * 按 SKU 编码查询（店铺端「填 SKU_CODE 预填」用）。
     * goods_sku.sku_code 无唯一索引，命中多条时按 id 升序取首条
     *
     * @param skuCode SKU 编码
     * @return 命中的 SKU；未命中返回 null
     */
    GoodsSku findFirstBySkuCode(String skuCode);

    /**
     * 按 SKU 编码统计条数（判断中台是否存在重复编码，配合 {@link #findFirstBySkuCode}）
     *
     * @param skuCode SKU 编码
     * @return 条数
     */
    long countBySkuCode(String skuCode);
}
