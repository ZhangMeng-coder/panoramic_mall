package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.store.entity.StoreGoodsSku;

import java.util.List;
import java.util.Map;

/**
 * 店铺在售商品 SKU 服务。
 * <p>own-entity 增删改查直接用 MyBatis-Plus 基类（IService）内置方法；此处只补
 * 「按 SPU 归属」的查询/级联删除能力，供 {@link StoreGoodsSpuService} 跨实体调用
 * （跨实体只走 owner service，不直接持有对方 Mapper）。SKU 不持 store_id，
 * 数据权限经其 SPU 归属判定。</p>
 */
public interface StoreGoodsSkuService extends IService<StoreGoodsSku> {

    /**
     * 某店铺商品下的全部 SKU（按 id 升序，保证列表顺序稳定）
     *
     * @param spuId 店铺商品 SPU id
     * @return SKU 列表；无则空列表
     */
    List<StoreGoodsSku> listBySpuId(Long spuId);

    /**
     * 批量统计各 SPU 的 SKU 数量（分页列表回填 skuCount 用，避免 N+1）
     *
     * @param spuIds SPU id 集合
     * @return spuId -> SKU 数；入参空则空 Map（无 SKU 的 SPU 不在 Map 中）
     */
    Map<Long, Integer> countMapBySpuIds(List<Long> spuIds);

    /**
     * 某店铺商品下是否存在已上架 SKU（SPU 上下架联动推导用）
     *
     * @param spuId 店铺商品 SPU id
     * @return true = 至少一个 SKU 处于上架态
     */
    boolean hasOnShelfSku(Long spuId);

    /**
     * 逻辑删除某店铺商品下的全部 SKU（SPU 删除时级联）
     *
     * @param spuId 店铺商品 SPU id
     */
    void removeBySpuId(Long spuId);
}
