package com.panoramic.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.store.entity.StoreGoodsEvaluation;

/**
 * 商品评价 Mapper
 * <p>无自定义语句：评分聚合与星级分布都走 MyBatis-Plus 的 {@code QueryWrapper}
 * （{@code select("score, COUNT(*) AS cnt").groupBy("score")}），与商品筛选聚合同一手法。</p>
 */
public interface StoreGoodsEvaluationMapper extends BaseMapper<StoreGoodsEvaluation> {
}
