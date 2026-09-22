package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.store.entity.StoreGoodsSkuStockLog;
import com.panoramic.store.mapper.StoreGoodsSkuStockLogMapper;
import com.panoramic.store.service.StoreGoodsSkuStockLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 店铺在售商品 SKU 库存变动流水服务实现。
 * <p>只增不改：无 update / remove 业务方法（见 {@link StoreGoodsSkuStockLog}）。
 * 读路径一次条件查询，写路径单条 INSERT，均不加外层 {@code @Transactional}——
 * 事务边界由调用方（库存服务的扣减 / 回补，两者自带事务，流水与库存变更必须同成同败）划定。</p>
 */
@Slf4j
@Service
public class StoreGoodsSkuStockLogServiceImpl extends ServiceImpl<StoreGoodsSkuStockLogMapper, StoreGoodsSkuStockLog>
        implements StoreGoodsSkuStockLogService {

    @Override
    public void saveChange(Long skuId, String orderNo, String kind, int changeQuantity, LocalDateTime occurredAt) {
        StoreGoodsSkuStockLog row = new StoreGoodsSkuStockLog();
        row.setSkuId(skuId);
        row.setOrderNo(orderNo);
        row.setKind(kind);
        row.setChangeQuantity(changeQuantity);
        row.setOccurredAt(occurredAt);
        // 审计列（create_user/create_time/...）由 MyMetaObjectHandler 经 save 自动填充，不手写
        save(row);
    }

    @Override
    public List<StoreGoodsSkuStockLog> listByOrderNoAndKind(String orderNo, String kind) {
        if (!StringUtils.hasText(orderNo) || !StringUtils.hasText(kind)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<StoreGoodsSkuStockLog>lambdaQuery()
                .eq(StoreGoodsSkuStockLog::getOrderNo, orderNo)
                .eq(StoreGoodsSkuStockLog::getKind, kind));
    }

    @Override
    public boolean exists(String orderNo, Long skuId, String kind) {
        if (!StringUtils.hasText(orderNo) || skuId == null || !StringUtils.hasText(kind)) {
            return false;
        }
        return count(Wrappers.<StoreGoodsSkuStockLog>lambdaQuery()
                .eq(StoreGoodsSkuStockLog::getOrderNo, orderNo)
                .eq(StoreGoodsSkuStockLog::getSkuId, skuId)
                .eq(StoreGoodsSkuStockLog::getKind, kind)) > 0;
    }
}
