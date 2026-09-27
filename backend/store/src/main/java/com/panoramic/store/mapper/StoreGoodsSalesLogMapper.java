package com.panoramic.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.store.entity.StoreGoodsSalesLog;

/**
 * 店铺商品销量台账 Mapper
 * <p>无手写语句：台账只增不改，读写口径都是基类能力（见 {@link StoreGoodsSalesLog}）。</p>
 */
public interface StoreGoodsSalesLogMapper extends BaseMapper<StoreGoodsSalesLog> {
}
