package com.panoramic.trade.order.domain.port;

import java.util.Collection;
import java.util.Map;

/**
 * 商品只读查询端口（跨域能力的内存适配器见 infrastructure；真实实现将指向 store 域）。
 *
 * <p>⚠ 为什么是「批量 map」而不是「逐个 get」：goods-check 与 price-compute 都要按 skuId 取商品，
 * 一次下单至少要查两遍（步骤自洽，不共享产出——裁定 D9）。以 {@code Collection} 入参、{@code Map}
 * 出参的形状，让未来换真实实现时**天然可以一次批量调用**（避免 N 次跨服务往返），
 * 而调用方仍按 skuId 取值，不必关心底层是 1 次还是 N 次调用。</p>
 *
 * <p>⚠ 本端口**不抛「商品不存在」**：不存在的 id 就是不在返回的 map 里。是否把「缺失」当错误由调用方决定
 * （goods-check 视为「商品已下架或不可购买」，编排层分店铺时的缺失则是「商品不存在」），
 * 端口只负责如实回话——错误语义属于业务，不属于查询。</p>
 */
public interface GoodsQueryPort {

    /**
     * 按 skuId 批量取商品快照
     *
     * @param skuIds 待查的 SKU id 集合
     * @return skuId → 快照；**查不到的 id 不出现在 map 里**（不是 null 值）
     */
    Map<Long, SkuSnapshot> mapBySkuIds(Collection<Long> skuIds);
}
