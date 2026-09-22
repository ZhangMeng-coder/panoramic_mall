package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 交易侧 SKU 快照批量查询参数（store 域内部接口与调用方同源共享）。
 * <p>用途：<b>trade-center 的订单流水线</b>落订单明细快照时，按购物车行里的 SKU id 集合一次取回
 * 名称 / 图片 / 规格 / 价格 / 上下架 / 锁定 / 店铺状态 / 可用库存，替代逐行调单条接口。</p>
 * <p>⚠ 本 DTO 经 Feign 以 {@code POST + @RequestBody} 传输（集合字段不做 query string 序列化），
 * 与 {@link StoreGoodsSpuBatchQueryDTO} 同口径。</p>
 * <p>⚠ <b>没有作用域字段</b>：调用方是<b>域</b>（trade-center）而不是端 BFF，手上只有 skuId——
 * 按资源 id 操作，域内不判身份、不校验店铺归属（见 cross-cutting 第 24 条）。</p>
 */
@Data
public class StoreGoodsSkuBatchQueryDTO {

    /**
     * 待查 SKU id 集合（必填）。
     * <p>查不到的 id（SKU 或所属 SPU 已删除）<b>跳过</b>，不出现在出参里——由调用方按
     * 「拿不到 = 不存在」自行处置（下单快照会因此判为商品不可购买）。</p>
     */
    @NotEmpty(message = "请至少选择一个 SKU")
    private List<Long> skuIds;
}
