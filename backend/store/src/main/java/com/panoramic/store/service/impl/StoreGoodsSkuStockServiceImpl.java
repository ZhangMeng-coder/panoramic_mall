package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.store.entity.StoreGoodsSkuStock;
import com.panoramic.store.mapper.StoreGoodsSkuStockMapper;
import com.panoramic.store.service.StoreGoodsSkuStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺在售商品 SKU 库存服务实现
 * <p>读路径一次 {@code IN} 批量查、写路径单条条件 {@code UPDATE}，均不加外层 {@code @Transactional}
 * （单条语句自带事务，不拉长持锁时间）——见 store README R14。</p>
 */
@Slf4j
@Service
public class StoreGoodsSkuStockServiceImpl extends ServiceImpl<StoreGoodsSkuStockMapper, StoreGoodsSkuStock>
        implements StoreGoodsSkuStockService {

    @Override
    public Map<Long, StoreGoodsSkuStock> mapBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<StoreGoodsSkuStock> rows = list(Wrappers.<StoreGoodsSkuStock>lambdaQuery()
                .in(StoreGoodsSkuStock::getSkuId, skuIds));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, StoreGoodsSkuStock> map = new HashMap<>(rows.size());
        for (StoreGoodsSkuStock row : rows) {
            map.put(row.getSkuId(), row);
        }
        return map;
    }

    @Override
    public Map<Long, Integer> availableStockMapBySkuIds(Collection<Long> skuIds) {
        Map<Long, Integer> map = new HashMap<>();
        for (StoreGoodsSkuStock row : mapBySkuIds(skuIds).values()) {
            // 可用库存 = stock：locked_stock 已于 2026-09-21 废弃，不参与口径（DDL 删列见仓库根 todo.md 残留 1）
            map.put(row.getSkuId(), row.getStock() == null ? 0 : row.getStock());
        }
        return map;
    }

    @Override
    public void saveIfAbsent(Long skuId, Integer stock) {
        if (count(Wrappers.<StoreGoodsSkuStock>lambdaQuery()
                .eq(StoreGoodsSkuStock::getSkuId, skuId)) > 0) {
            // 已存在不动：防覆盖商户在库存页已设好的库存
            return;
        }
        StoreGoodsSkuStock row = new StoreGoodsSkuStock();
        row.setSkuId(skuId);
        row.setStock(stock == null || stock < 0 ? 0 : stock);
        // locked_stock 已废弃、不写；该列 NOT NULL DEFAULT 0，不设即 0
        // 审计列（create_user/create_time/...）由 MyMetaObjectHandler 经 save 自动填充，不手写
        save(row);
    }

    @Override
    public void removeBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        // StoreGoodsSkuStock 继承 BaseEntity（@TableLogic），remove 为逻辑删除
        remove(Wrappers.<StoreGoodsSkuStock>lambdaQuery().in(StoreGoodsSkuStock::getSkuId, skuIds));
    }

    @Override
    public void updateStock(Long skuId, Integer stock, Integer warnStock) {
        // 单条条件 UPDATE，只锁该库存行；update(null, wrapper) 不触发实体填充，
        // update_time 由库的 ON UPDATE CURRENT_TIMESTAMP 兜底
        update(null, Wrappers.<StoreGoodsSkuStock>lambdaUpdate()
                .eq(StoreGoodsSkuStock::getSkuId, skuId)
                .set(StoreGoodsSkuStock::getStock, stock)
                // warnStock 允许为 null（= 清除预警）：lambdaUpdate().set(col, null) 会显式写 NULL，
                // 语义正确（updateById 跳过 null 列，故此处必须用 set）
                .set(StoreGoodsSkuStock::getWarnStock, warnStock));
    }

    @Override
    public void batchUpdateStock(Collection<Long> skuIds, Integer stock) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        // 单条 IN 批量 UPDATE，不循环逐行
        update(null, Wrappers.<StoreGoodsSkuStock>lambdaUpdate()
                .in(StoreGoodsSkuStock::getSkuId, skuIds)
                .set(StoreGoodsSkuStock::getStock, stock));
    }
}
