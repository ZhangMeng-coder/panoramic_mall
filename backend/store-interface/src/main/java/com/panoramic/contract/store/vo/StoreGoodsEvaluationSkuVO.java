package com.panoramic.contract.store.vo;

import com.panoramic.contract.store.dto.SpecAttr;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评价的 SKU 快照条目（<b>入出参共用同一份形状</b>，store 域内部接口与各端 BFF 同源共享）。
 * <p><b>为什么是快照而不是引用</b>：评价对象是 SPU，但下单时的规格组合 / 单价 / 数量属于「成交那一刻的事实」，
 * 商品改价、SKU 被删都不应改写历史评价的展示。故提交时由调用方（mall-bff，它手上有订单明细）把该 SPU
 * 在本单里的全部 SKU 行组一起带进来，域侧原样落库。</p>
 * <p>⚠ 它同时被 {@code StoreGoodsEvaluationSubmitDTO}（入参）与 {@code StoreGoodsEvaluationPageItemVO}
 * （出参）引用——两侧形状必须一致，否则「提交进去的」与「读出来的」对不上。故只此一份，不各写一个。</p>
 */
@Data
public class StoreGoodsEvaluationSkuVO {

    /**
     * SKU id（历史快照，不保证该 SKU 仍在）
     */
    private Long skuId;

    /**
     * 规格组合（下单时的快照）
     */
    private List<SpecAttr> specAttrs;

    /**
     * 下单时单价
     */
    private BigDecimal unitPrice;

    /**
     * 下单数量
     */
    private Integer quantity;
}
