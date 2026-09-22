package com.panoramic.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.store.entity.StoreGoodsSkuStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 店铺在售商品 SKU 库存 Mapper
 */
public interface StoreGoodsSkuStockMapper extends BaseMapper<StoreGoodsSkuStock> {

    /**
     * 原子扣减库存（<b>唯一判据是影响行数</b>）：库存够才扣，不够则命中 0 行。
     * <p>「库存够不够」与「扣减」必须在同一条语句里判定，否则先查后改会让并发下单超卖——
     * 故这里不写「先 SELECT 再 UPDATE」的两步式实现。</p>
     * <p>⚠ 参数一律 {@code #{}} 绑定（预编译占位符），不得用 {@code setSql} 拼串：拼接会把数量与 skuId
     * 变成注入面。{@code locked_stock} 已于 2026-09-21 废弃，不参与判据、条件里也不出现。</p>
     *
     * @param skuId    SKU id
     * @param quantity 扣减数量（调用方保证 &gt; 0）
     * @return 影响行数：1 = 扣减成功；0 = 库存不足或该 SKU 无库存行（两者对本方法等价）
     */
    @Update("UPDATE store_goods_sku_stock SET stock = stock - #{quantity} "
            + "WHERE sku_id = #{skuId} AND stock >= #{quantity} AND is_delete = 0")
    int deductIfEnough(@Param("skuId") Long skuId, @Param("quantity") int quantity);
}
