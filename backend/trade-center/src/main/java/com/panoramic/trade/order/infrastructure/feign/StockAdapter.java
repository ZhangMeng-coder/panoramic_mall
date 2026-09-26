package com.panoramic.trade.order.infrastructure.feign;

import com.panoramic.common.feign.DomainResp;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.StoreGoodsSkuBatchQueryDTO;
import com.panoramic.contract.store.dto.StoreStockDeductDTO;
import com.panoramic.contract.store.vo.StoreGoodsSkuSnapshotVO;
import com.panoramic.trade.order.domain.port.StockPort;

import java.util.List;

/**
 * {@link StockPort} 的真实实现：经 {@code StoreClient} 调 store 域的库存写入（扣减 / 按单回补）。
 *
 * <p>⚠ <b>原子性由 store 域提供</b>：扣减在那边是一条 {@code UPDATE ... WHERE stock >= ?} 的条件更新、
 * 以影响行数为唯一判据，并在同一事务里写一条出库流水——端口要求的「判断够不够 → 扣 → 记账」三合一，
 * 边界在数据库上，与本域无关。本类只做入参组装与取值翻译。</p>
 *
 * <p>⚠ <b>不吞异常、不做降级</b>（见 cross-cutting 第 24 条）：store 不可达 / 熔断打开时异常原样上抛，
 * 整次下单失败。唯一的「不抛」是 {@link #deduct} 返回 {@code false}——那**不是故障**，是库存不足
 * （store 域以 {@code code=200} + {@code data=false} 表达，R18），由 stock-check 翻成 400「库存不足」给页面。</p>
 */
public class StockAdapter implements StockPort {

    private final StoreClient storeClient;

    public StockAdapter(StoreClient storeClient) {
        this.storeClient = storeClient;
    }

    @Override
    public boolean deduct(Long skuId, int quantity, String orderNo) {
        StoreStockDeductDTO dto = new StoreStockDeductDTO();
        dto.setSkuId(skuId);
        dto.setQuantity(quantity);
        dto.setOrderNo(orderNo);
        // ⚠ DomainResp.unwrap 而非 BffFeignCall：本类是域间调用，**不解包成降级文案**——
        //   域侧 code=400/404 要作为业务异常原样上抛（第 24 条），只有 code=200 才取 data。
        //   data=false（库存不足，R18）照旧返回 false，不能翻成异常。
        Boolean deducted = DomainResp.unwrap(storeClient.deductStock(dto));
        return Boolean.TRUE.equals(deducted);
    }

    @Override
    public void revertByOrder(String orderNo) {
        // 补偿路径：store 域侧对「单号为空 / 该单没扣过 / 库存行已不存在」一律静默成功（R19），
        // 故这里也没有任何分支要写——异常照旧上抛，由编排器挂 addSuppressed、不覆盖主异常
        DomainResp.unwrap(storeClient.revertStockByOrder(orderNo));
    }

    /**
     * 当前可用库存：走**同一条**交易侧快照批量接口取 {@code availableStock}
     *
     * <p>⚠ store 域没有单独的可售库存端点——就是为此在 {@link StoreGoodsSkuSnapshotVO} 里留了
     * {@code availableStock} 这个字段，故这里复用批量快照（单个 skuId 一条查询）。
     * 查不到（SKU / SPU 已删除，或库存行缺失）按端口 javadoc 的口径返回 <b>0</b>：
     * 没登记过的 SKU 就是可售 0 件，与商品详情出参同口径。</p>
     *
     * <p>⚠ 本方法在 main 代码里没有调用点（库存够不够由 store 域的原子条件更新回答，不该先读后写），
     * 但它是端口契约的一部分，必须实现。</p>
     */
    @Override
    public int available(Long skuId) {
        if (skuId == null) {
            return 0;
        }
        StoreGoodsSkuBatchQueryDTO query = new StoreGoodsSkuBatchQueryDTO();
        query.setSkuIds(List.of(skuId));
        List<StoreGoodsSkuSnapshotVO> rows = DomainResp.unwrap(storeClient.tradeSkuSnapshotBatch(query));
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        for (StoreGoodsSkuSnapshotVO row : rows) {
            if (row != null && skuId.equals(row.getSkuId())) {
                return row.getAvailableStock() == null ? 0 : row.getAvailableStock();
            }
        }
        return 0;
    }
}
