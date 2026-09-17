package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.store.entity.StoreGoodsSku;
import com.panoramic.store.mapper.StoreGoodsSkuMapper;
import com.panoramic.store.service.StoreGoodsSkuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public BigDecimal minPriceBySpuId(Long spuId) {
        // 取全量再求最小是有意的：本方法**只在写路径**被调用（save/replaceSkus/updateSkuShelf/lock），
        // 读路径一律直接读 store_goods_spu.min_price 冗余列（这正是加该列的理由），故不存在 N+1。
        // 一个 SPU 的 SKU 数是个位数，不值得为它引入字符串列名的聚合查询。
        List<StoreGoodsSku> onShelf = list(Wrappers.<StoreGoodsSku>lambdaQuery()
                .eq(StoreGoodsSku::getSpuId, spuId)
                .eq(StoreGoodsSku::getShelfStatus, StoreGoodsSku.SHELF_ON));
        return onShelf.stream()
                .map(StoreGoodsSku::getPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    @Override
    public void removeBySpuId(Long spuId) {
        // StoreGoodsSku 继承 BaseEntity（@TableLogic），remove 为逻辑删除
        remove(Wrappers.<StoreGoodsSku>lambdaQuery().eq(StoreGoodsSku::getSpuId, spuId));
    }

    @Override
    public boolean offShelfBySpuId(Long spuId) {
        // 条件更新只命中上架行；update(null, wrapper) 不触发实体填充，update_time 由库的 ON UPDATE 兜底
        return update(null, Wrappers.<StoreGoodsSku>lambdaUpdate()
                .eq(StoreGoodsSku::getSpuId, spuId)
                .eq(StoreGoodsSku::getShelfStatus, StoreGoodsSku.SHELF_ON)
                .set(StoreGoodsSku::getShelfStatus, StoreGoodsSku.SHELF_OFF));
    }
}
