package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.store.entity.StoreGoodsSkuStockLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺在售商品 SKU 库存变动流水服务（{@code store_goods_sku_stock_log} 的<b>唯一 owner</b>）。
 * <p><b>只增不改</b>：流水是历史事实，本接口只暴露「记一条」与两个查询，不提供 update / remove 能力
 * （需要抵消就记一条反向流水）；故库存服务要落流水时一律经本接口，不得自行持有流水 Mapper。</p>
 * <p>本服务不认识 {@code store_id}、不做归属校验，也不判断扣减是否成功——它只忠实记录调用方写入的事实。
 * 「扣减成功才记 OUT」「尚无 REVERT 才回补」这两条判据在
 * {@link StoreGoodsSkuStockService#deduct} / {@link StoreGoodsSkuStockService#revertByOrder} 里。</p>
 */
public interface StoreGoodsSkuStockLogService extends IService<StoreGoodsSkuStockLog> {

    /**
     * 记一条库存变动流水（审计列由 {@code MyMetaObjectHandler} 自动填充，不手写）。
     *
     * @param skuId          SKU id
     * @param orderNo        订单号
     * @param kind           变动方向：{@link StoreGoodsSkuStockLog#KIND_OUT} / {@link StoreGoodsSkuStockLog#KIND_REVERT}
     * @param changeQuantity 变动数量（恒正，方向由 {@code kind} 表达）
     * @param occurredAt     变动发生时间（业务时间，由调用方带入）
     */
    void saveChange(Long skuId, String orderNo, String kind, int changeQuantity, LocalDateTime occurredAt);

    /**
     * 按订单号 + 变动方向取流水（扣减 / 回补对账用）
     *
     * @param orderNo 订单号
     * @param kind    变动方向
     * @return 流水列表；无则空列表（不抛异常）
     */
    List<StoreGoodsSkuStockLog> listByOrderNoAndKind(String orderNo, String kind);

    /**
     * 该单该 SKU 该方向的流水是否已存在（<b>回补幂等</b>的判据）
     *
     * @param orderNo 订单号
     * @param skuId   SKU id
     * @param kind    变动方向
     * @return true = 已记过该方向的流水
     */
    boolean exists(String orderNo, Long skuId, String kind);
}
