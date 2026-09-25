package com.panoramic.contract.store.vo;

import lombok.Data;

import java.util.List;

/**
 * 评价星级分布（「1 星 N 条、2 星 M 条…」+ 总条数）。
 * <p>两个调用方：<b>C 端商品详情页</b>（看该商品的分布）与<b>商户端评价页</b>（看本店整体分布）。</p>
 * <p>⚠ {@code scores} <b>恒为 5 项、按 1→5 升序、无评价的星级也占位补 0</b>：
 * 前端拿到就能直接画五条柱，不必自己补缺失星级，也不会因为「这页恰好没人打 3 星」而让柱子错位。</p>
 */
@Data
public class StoreGoodsEvaluationStatVO {

    /**
     * 总条数（= 各星级条数之和）
     */
    private Long total;

    /**
     * 星级分布（固定 1 ~ 5 五行，升序；无评价的星级 count 为 0）
     */
    private List<StoreGoodsEvaluationScoreCountVO> scores;
}
