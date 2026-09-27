package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 销量入账明细中的**一行**（一个 SPU 在该单里的合计件数）。
 * <p>用途：{@link StoreGoodsSalesPushDTO} 的列表元素，形状与既有的 SKU 替换 / 批量库存更新
 * （{@code StoreGoodsSkuReplaceDTO#skus}）同款——**列表元素是一个具名 DTO**，不是 Map、不是位置裸参。</p>
 * <p>⚠ <b>为什么由调用方给 {@code spuId + quantity}，本域不自己去订单里取</b>：本域不持订单表、
 * 也没有任何通往 trade 域的调用边（cross-cutting 第 24 条：唯一的跨域边是 trade → store，
 * 方向相反的那条不存在）。故订单项只能由调用方随推送带过来。</p>
 * <p>⚠ {@code quantity} 是**该 SPU 在该单里的合计件数**（调用方按 {@code spuId} 聚合订单项后给出）：
 * 同一订单里同一 SPU 下的多个 SKU 行不应各推一次，否则同一个 {@code (orderNo, spuId)} 会撞台账唯一键——
 * 撞了就整条推送失败（见 {@link StoreGoodsSalesPushDTO} 的幂等口径）。</p>
 */
@Data
public class StoreGoodsSalesItemDTO {

    /**
     * 店铺商品 SPU id（必填；`store_goods_spu.id`）
     */
    @NotNull(message = "商品 SPU 不能为空")
    private Long spuId;

    /**
     * 该单该 SPU 的合计件数（必填，≥1）
     */
    @NotNull(message = "销量不能为空")
    @Min(value = 1, message = "销量至少为 1")
    private Integer quantity;
}
