package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.store.entity.StoreGoodsSkuStock;

import java.util.Collection;
import java.util.Map;

/**
 * 店铺在售商品 SKU 库存服务。
 * <p>库存读写<b>只碰 {@code store_goods_sku_stock} 一张表</b>：不 join、不锁 {@code store_goods_sku} /
 * {@code store_goods_spu} 行，不用 {@code SELECT ... FOR UPDATE}，批量走单条
 * {@code UPDATE ... WHERE sku_id IN (...)}（R14 锁隔离规约）。</p>
 * <p>⚠ <b>SKU 归属校验由调用方负责</b>：本 service 不持 SKU / SPU、不认识 {@code store_id}，
 * 只按 {@code skuId} 读写——owner 侧的「属于本店」与「平台锁定期只读」判定在
 * {@code StoreGoodsSpuServiceImpl} 的归属链上做。</p>
 * <p>own-entity 增删改查直接用 MyBatis-Plus 基类（{@code IService}）内置方法，
 * 此处只补批量读与批量写。</p>
 */
public interface StoreGoodsSkuStockService extends IService<StoreGoodsSkuStock> {

    /**
     * 批量读整行库存（库存页回填用，一次 IN 查询，无 N+1）
     *
     * @param skuIds SKU id 集合
     * @return skuId -> 库存行；入参空则空 Map，无库存行的 SKU 不在 Map 中
     */
    Map<Long, StoreGoodsSkuStock> mapBySkuIds(Collection<Long> skuIds);

    /**
     * 批量读可用库存（SKU VO 回填用，一次 IN 查询）
     *
     * @param skuIds SKU id 集合
     * @return skuId -> 可用库存（{@code stock}，空列按 0 计）；无库存行的 SKU 不在 Map 中
     */
    Map<Long, Integer> availableStockMapBySkuIds(Collection<Long> skuIds);

    /**
     * 为某 SKU 补建库存行（新建 SKU 时调用）；已存在则<b>只补不覆盖</b>，不动商户已设的库存
     *
     * @param skuId SKU id
     * @param stock 初始总库存；null 或负数按 0
     */
    void saveIfAbsent(Long skuId, Integer stock);

    /**
     * 逻辑删除一批 SKU 的库存行（SKU / SPU 级联删除时调用）
     *
     * @param skuIds SKU id 集合；空集合直接返回
     */
    void removeBySkuIds(Collection<Long> skuIds);

    /**
     * 改单行 SKU 库存（单条条件 UPDATE，只锁该库存行）
     *
     * @param skuId     SKU id
     * @param stock     总库存
     * @param warnStock 低库存预警阈值；<b>传 null = 清除预警</b>（用条件更新显式写 NULL，
     *                  {@code updateById} 会跳过 null 列，故此处不能走它）
     */
    void updateStock(Long skuId, Integer stock, Integer warnStock);

    /**
     * 批量设置一批 SKU 的总库存（单条 IN 更新，不循环逐行）；不动 {@code warn_stock}
     *
     * @param skuIds SKU id 集合；空集合直接返回
     * @param stock  总库存
     */
    void batchUpdateStock(Collection<Long> skuIds, Integer stock);
}
