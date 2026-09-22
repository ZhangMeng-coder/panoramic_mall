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
 * 此处只补批量读、批量写与交易协作的两条增减（{@link #deduct} / {@link #revertByOrder}）。</p>
 * <p><b>交易协作的增减（域间调用，cross-cutting 第 24 条）无作用域锚点</b>：按资源 id 操作，
 * 域内不判身份、不校验店铺归属——调用方是 trade-center 的订单流水线，它手上只有 skuId 与订单号。
 * 库存表的原子条件更新是<b>唯一事实源</b>，流水表（经
 * {@link StoreGoodsSkuStockLogService}）只留痕；两者在同一条路径上同成同败。</p>
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

    // ---- 交易协作（域间调用，cross-cutting 第 24 条：无作用域锚点、按资源 id 操作、域内不判身份）----

    /**
     * 原子扣减库存（下单出库）：库存表上一条原子条件更新，<b>影响行数是唯一判据</b>。
     * <p>成功（影响 1 行）时记一条 {@code OUT} 流水；库存不足（影响 0 行）返回 {@code false} 且
     * <b>不记流水</b>。入参不合法（{@code skuId} 空 / {@code quantity <= 0} / {@code orderNo} 空）
     * 直接返回 {@code false}——不写流水、不抛异常。</p>
     * <p>⚠ <b>库存不足不是 HTTP 错误</b>（R18）：本方法返回 {@code false}（HTTP 200），
     * 由交易域在其 {@code StockCheckStep} 里翻成 400「库存不足」——store 域不替调用方决定那是错误。</p>
     * <p>⚠ <b>不判锁定、不判上下架</b>（R19）：可见性由交易侧的 goods 校验步骤判；本方法只管库存数够不够。
     * 库存行不存在同样按影响 0 行处理（与「库存不足」等价）。</p>
     * <p>库存变更与流水写入在<b>同一事务</b>内（{@code @Transactional(rollbackFor = Exception.class)}），
     * 二者同成同败——不会出现「扣了没记账」或「记了没扣」。</p>
     *
     * @param skuId    SKU id
     * @param quantity 扣减数量（须 &gt; 0）
     * @param orderNo  订单号（流水的配对键，回补按它找回来）
     * @return true = 扣减成功；false = 未扣减（入参不合法 / 库存不足）
     */
    boolean deduct(Long skuId, int quantity, String orderNo);

    /**
     * 按订单号回补库存（取消 / 超时 / 支付失败后的补偿路径）。
     * <p>取该单的全部 {@code OUT} 流水，逐条对<b>尚无对应 {@code REVERT} 流水</b>的 SKU 做
     * <b>无守卫</b>回补（{@code stock = stock + 数量}）并记一条 {@code REVERT} 流水；
     * 已有 {@code REVERT} 的跳过——故重复调用是 no-op（幂等，按 {@code exists} 判）。</p>
     * <p>⚠ <b>补偿路径宁可不做也不能炸</b>（R19）：{@code orderNo} 为空、该单没有 {@code OUT} 流水、
     * 甚至库存行已不存在，都<b>静默 no-op</b>，绝不抛异常——它在失败回滚链路上跑，抛异常会把
     * 「已经失败的订单」再炸一次，反而丢掉本该归还的库存。</p>
     * <p>⚠ 回补<b>不判平台锁定</b>：锁定期只冻结 owner 侧的可售操作，已经发生的订单必须能归还库存。</p>
     * <p>全部回补在同<b>一事务</b>内（{@code @Transactional(rollbackFor = Exception.class)}）。</p>
     *
     * @param orderNo 订单号
     */
    void revertByOrder(String orderNo);
}
