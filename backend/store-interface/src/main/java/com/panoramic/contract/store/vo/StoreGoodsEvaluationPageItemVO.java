package com.panoramic.contract.store.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价分页列表项（store 域内部接口出参；<b>两端 BFF 都不直接下发它</b>，各自加工成私有页面类型）。
 * <p>出参带 {@code customerId}（评价人账号 id）而非昵称头像：顾客资料在 customer-center 域，
 * store 域不持、也不跨域去取——由端 BFF 收口后一次批量补昵称头像（见 cross-cutting 第 3 条与
 * customer-center 的 {@code listProfilesByIds}）。这也是两端页面类型必须是各自私有类型的原因。</p>
 * <p>{@code spuName} 与 {@code skuSnapshot} 都是<b>出参侧补齐</b>的展示字段：
 * {@code skuSnapshot} 是落库快照（读出来反序列化），{@code spuName} 是域内批量回查当前商品名
 * （商品改名后评价列表显示新名，与其它列表口径一致）。</p>
 */
@Data
public class StoreGoodsEvaluationPageItemVO {

    /**
     * 评价 id
     */
    private Long id;

    /**
     * 被评价的商品 SPU id
     */
    private Long spuId;

    /**
     * 商品名称（域内批量回查）。
     * <p>⚠ <b>商品已软删时为 {@code null}</b>——不做「商品已删除」之类的兜底文案，
     * 那是各端 BFF 的展示决定（0 与 null 不可互代同款纪律）。</p>
     */
    private String spuName;

    /**
     * 下单时的 SKU 快照（该 SPU 在本单里的全部 SKU 行组）
     */
    private List<StoreGoodsEvaluationSkuVO> skuSnapshot;

    /**
     * 评分：1 ~ 5 星
     */
    private Integer score;

    /**
     * 评价文字（可空）
     */
    private String content;

    /**
     * 评价人（= mall_user.id）；昵称头像由端 BFF 批量补
     */
    private Long customerId;

    /**
     * 评价时间
     */
    private LocalDateTime createTime;

    /**
     * 商家回复内容（{@code null} = 未回复）
     */
    private String replyContent;

    /**
     * 商家回复时间（{@code null} = 未回复）
     */
    private LocalDateTime replyTime;
}
