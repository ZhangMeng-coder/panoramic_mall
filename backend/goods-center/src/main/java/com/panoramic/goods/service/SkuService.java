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
}
