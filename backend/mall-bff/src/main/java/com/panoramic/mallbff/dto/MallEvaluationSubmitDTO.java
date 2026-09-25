package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交商品评价请求参数（页面级，{@code POST /evaluations} 的入参）。
 *
 * <p>⚠ <b>没有 {@code customerId} / {@code storeId}</b>（cross-cutting 第 22 条）：评价人取自登录态、
 * 店铺由域内按 {@code spuId} 反查——两者都不出现在页面契约里。</p>
 *
 * <p>⚠ <b>也没有 {@code skuSnapshot}</b>：快照由本层从<b>订单明细</b>归组得出（一单同一 SPU 的多个 SKU
 * 合成一条评价），页面无权自造规格 / 单价 / 数量——那些是「成交那一刻的事实」，只能来自订单。</p>
 *
 * <p>约束<b>与域侧 {@code StoreGoodsEvaluationSubmitDTO} 镜像</b>（1~5 星、文字 500 字）：
 * BFF 是页面边界，坏输入该在<b>这里</b>回 400，而不是穿到域里再经熔断语义绕一圈绕回来。
 * ⚠ 「订单已完成」是<b>业务前置条件</b>而不是入参格式，故不在这里校验——它在 service 里经 trade-center 判。</p>
 */
@Data
public class MallEvaluationSubmitDTO {

    /**
     * 订单号（评价归属的完成态订单；⚠ 单号是业务可读标识，自增 id 不出现在契约里）
     */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /**
     * 被评价的商品 SPU id（= 订单明细里该商品的 {@code spuId}）
     */
    @NotNull(message = "商品ID不能为空")
    private Long spuId;

    /**
     * 评分：1 ~ 5 星（整星，默认口径）
     */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能低于1星")
    @Max(value = 5, message = "评分不能高于5星")
    private Integer score;

    /**
     * 评价文字（可空；<b>纯文本</b>，前端按插值渲染、不许 {@code v-html}）
     */
    @Size(max = 500, message = "评价内容最多500字")
    private String content;
}
