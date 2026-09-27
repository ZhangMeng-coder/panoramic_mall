package com.panoramic.contract.store.vo;

import lombok.Data;

/**
 * 店铺规模统计响应（store 域内部接口与 admin BFF 同源共享）。
 * <p><b>两个计数、同为当前累计快照、合集在一个接口</b>（不是一个窗口一个快照）：它们同属「店铺规模」
 * 这一件事，拆成两条只会让调用方多跑一次跨服务往返；「店铺商品」本就是<b>店铺的二级资源</b>
 * （同 {@code /goods/evaluation/**} 之于商品）。</p>
 * <p>⚠ <b>无入参、无窗口</b>：两个数都没有时间范围概念（店铺数不是「本月新增店铺数」，
 * 商品数不是「本月新增商品数」）。别为了「和订单统计对称」给它加 start/end——加了也没有对应语义。</p>
 * <p>⚠ 口径（哪些店铺、哪些商品计入）属<b>业务规则</b>，见 {@code backend/store/README.md}；
 * 两个计数都<b>不受店铺审核状态约束</b>的细节在那边一并写清。</p>
 */
@Data
public class ShopStatsVO {

    /**
     * 店铺数（口径见 store README：只计<b>审核已通过</b>的店铺）
     */
    private Long shopCount;

    /**
     * 店铺商品数（口径见 store README：<b>未删除的全部 SPU</b>，不分上架状态、不分店铺审核状态）
     */
    private Long goodsCount;
}
