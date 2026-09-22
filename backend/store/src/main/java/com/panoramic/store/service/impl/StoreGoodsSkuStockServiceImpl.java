package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.store.entity.StoreGoodsSkuStock;
import com.panoramic.store.entity.StoreGoodsSkuStockLog;
import com.panoramic.store.mapper.StoreGoodsSkuStockMapper;
import com.panoramic.store.service.StoreGoodsSkuStockLogService;
import com.panoramic.store.service.StoreGoodsSkuStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 店铺在售商品 SKU 库存服务实现
 * <p>读路径一次 {@code IN} 批量查、写路径单条条件 {@code UPDATE}，均不加外层 {@code @Transactional}
 * （单条语句自带事务，不拉长持锁时间）——见 store README R14。</p>
 * <p><b>交易协作的扣减 / 回补是这条规约的例外</b>：它们各自要写两张表（库存 + 流水），
 * 故带 {@code @Transactional(rollbackFor = Exception.class)}——「扣了没记账」比「多持一会儿锁」贵得多。
 * 例外只在这两个方法上，其它方法仍不加事务。</p>
 * <p>流水<b>只经 {@link StoreGoodsSkuStockLogService}</b> 写（跨实体只走 owner service），
 * 本类不持流水 Mapper。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreGoodsSkuStockServiceImpl extends ServiceImpl<StoreGoodsSkuStockMapper, StoreGoodsSkuStock>
        implements StoreGoodsSkuStockService {

    /** 跨实体：库存变动流水（流水表读写只走它；本类不直接持有流水 Mapper） */
    private final StoreGoodsSkuStockLogService stockLogService;

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
            // 可用库存 = stock：locked_stock 已于 2026-09-21 废弃、2026-09-22 删列，不参与口径
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

    // ---- 交易协作（域间调用：无作用域锚点、按资源 id 操作、域内不判身份；见 cross-cutting 第 24 条）----

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(Long skuId, int quantity, String orderNo) {
        if (skuId == null || quantity <= 0 || !StringUtils.hasText(orderNo)) {
            // 入参不合法：直接未扣减（不写流水、不抛异常）——调用方拿到 false 自行判处置。
            // ⚠ 这里与 StockPort 的「false ⇔ 库存不足」有一处**刻意分叉**：非法入参也压成 false（线上经入口的
            //    Bean Validation 拦下，不可达）；trade 域的测试替身 InMemoryStockPort 对 quantity<=0 是抛
            //    IllegalArgumentException。两者只在「非法入参」这一维上分叉，正常路径同语义。
            return false;
        }
        // 原子条件更新：影响行数是唯一判据（0 行 = 库存不足，也可能是该 SKU 没有库存行，两者等价）
        int rows = baseMapper.deductIfEnough(skuId, quantity);
        if (rows == 0) {
            // R18：库存不足**不是** HTTP 错误，不记流水、不抛异常，返回 false 由交易域翻成业务错误
            return false;
        }
        stockLogService.saveChange(skuId, orderNo, StoreGoodsSkuStockLog.KIND_OUT, quantity, LocalDateTime.now());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revertByOrder(String orderNo) {
        // R19：补偿路径宁可不做也不能炸——入参空 / 该单没扣过 / 库存行已不存在，一律静默成功。
        // ⚠ 措辞收窄（T3 复评）：能保证的是「这两种情形不抛」，不是「本方法绝不抛」——**并发**对同一单号回补时，
        //    后到者仍可能撞流水唯一键而抛（fail-closed：其事务整体回滚，不会多补库存；调用方
        //    OrderCreateCoordinator#revertCreated 用 addSuppressed 兜住，不覆盖主异常）。
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        List<StoreGoodsSkuStockLog> outs =
                stockLogService.listByOrderNoAndKind(orderNo, StoreGoodsSkuStockLog.KIND_OUT);
        if (outs == null || outs.isEmpty()) {
            log.info("按单回补：该单无扣减流水，跳过。orderNo={}", orderNo);
            return;
        }
        for (StoreGoodsSkuStockLog out : outs) {
            // 已有 REVERT 的跳过（幂等）：重复调用本方法不会重复补库存。
            // ⚠ 这不是 StockPort javadoc 写的「按净额归还」，只在 `uk_order_sku_kind` 保证
            //    「同 (orderNo, skuId) 至多一条 OUT」时才与净额口径等价——**该键一旦被放松或流水行被清理，
            //    必须改成净额算法**（OUT 合计 − REVERT 合计，只补正数部分），否则会**静默少补**。
            if (stockLogService.exists(orderNo, out.getSkuId(), StoreGoodsSkuStockLog.KIND_REVERT)) {
                continue;
            }
            int quantity = out.getChangeQuantity() == null ? 0 : out.getChangeQuantity();
            // 回补一律**无守卫**（不加 stock >= x 之类的条件）：补的是已经扣掉的那份，没有「补不了」的情形。
            // ⚠ 库存行不在了也不会报错——影响 0 行无妨，流水照记（历史事实不因现状缺行而丢）
            update(null, Wrappers.<StoreGoodsSkuStock>lambdaUpdate()
                    .eq(StoreGoodsSkuStock::getSkuId, out.getSkuId())
                    // setSql 用 {0} 占位（MP 会做参数替换），不拼串；数量是 DB 读回的行值，非外部入参
                    .setSql("stock = stock + {0}", quantity));
            stockLogService.saveChange(out.getSkuId(), orderNo, StoreGoodsSkuStockLog.KIND_REVERT,
                    quantity, LocalDateTime.now());
        }
    }
}
