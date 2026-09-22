package com.panoramic.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.store.entity.StoreGoodsSkuStockLog;

/**
 * 店铺在售商品 SKU 库存变动流水 Mapper
 * <p>无手写语句：流水只增不改，读写口径都是基类能力（见 {@link StoreGoodsSkuStockLog}）。</p>
 */
public interface StoreGoodsSkuStockLogMapper extends BaseMapper<StoreGoodsSkuStockLog> {
}
