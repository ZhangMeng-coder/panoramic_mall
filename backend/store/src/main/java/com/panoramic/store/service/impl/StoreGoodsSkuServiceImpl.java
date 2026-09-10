package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.store.entity.StoreGoodsSku;
import com.panoramic.store.mapper.StoreGoodsSkuMapper;
import com.panoramic.store.service.StoreGoodsSkuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 店铺在售商品 SKU 服务实现
 */
@Slf4j
@Service
public class StoreGoodsSkuServiceImpl extends ServiceImpl<StoreGoodsSkuMapper, StoreGoodsSku>
        implements StoreGoodsSkuService {

    @Override
    public List<StoreGoodsSku> listBySpuId(Long spuId) {
        List<StoreGoodsSku> skus = list(Wrappers.<StoreGoodsSku>lambdaQuery()
                .eq(StoreGoodsSku::getSpuId, spuId)
                .orderByAsc(StoreGoodsSku::getId));
        return skus == null ? Collections.emptyList() : skus;
    }

    @Override
    public Map<Long, Integer> countMapBySpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 只取 spu_id 列做分组计数，避免整行回表
        List<StoreGoodsSku> skus = list(Wrappers.<StoreGoodsSku>lambdaQuery()
                .select(StoreGoodsSku::getSpuId)
                .in(StoreGoodsSku::getSpuId, spuIds));
        if (skus == null) {
            return Collections.emptyMap();
        }
        return skus.stream().collect(Collectors.groupingBy(StoreGoodsSku::getSpuId,
                Collectors.summingInt(sku -> 1)));
    }

    @Override
    public boolean hasOnShelfSku(Long spuId) {
        return count(Wrappers.<StoreGoodsSku>lambdaQuery()
                .eq(StoreGoodsSku::getSpuId, spuId)
                .eq(StoreGoodsSku::getShelfStatus, StoreGoodsSku.SHELF_ON)) > 0;
    }

    @Override
    public void removeBySpuId(Long spuId) {
        // StoreGoodsSku 继承 BaseEntity（@TableLogic），remove 为逻辑删除
        remove(Wrappers.<StoreGoodsSku>lambdaQuery().eq(StoreGoodsSku::getSpuId, spuId));
    }
}
