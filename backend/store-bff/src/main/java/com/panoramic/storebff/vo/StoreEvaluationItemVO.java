package com.panoramic.storebff.vo;

import com.panoramic.contract.store.vo.StoreGoodsEvaluationSkuVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺端 BFF · 本店评价列表项（<b>页面出参</b>，本层私有，不继承任何域类型）。
 *
 * <p>⚠ <b>与域侧 {@code StoreGoodsEvaluationPageItemVO} 是两份类型</b>：域出参带 {@code customerId}
 * （内部账号 id，<b>不下发</b>给页面），本层把它<b>替换</b>成 {@code nickname} / {@code avatar}
 * ——这两个字段在 customer-center 域，store 域不持、也不跨域去取，只有端 BFF 才收口得出。
 * 映射<b>逐字段手工写</b>（不用 {@code BeanUtils.copyProperties}，同 {@code StoreGoodsSpuDetailBffVO} 的做法）：
 * 域侧将来多出字段时，下发面不跟着自动扩大（cross-cutting 第 17 条）。</p>
 *
 * <p>⚠ {@code skuSnapshot} 是<b>落库快照</b>（下单那一刻的规格 / 单价 / 数量），直接复用域契约类型
 * {@link StoreGoodsEvaluationSkuVO}：它本就是「入出参共用一份形状」的类型，两端 BFF 也都照此下发，
 * 不为它再抄一份。</p>
 *
 * <p>⚠ <b>无星级分布字段</b>：商户端要的是<b>筛选</b>（入参 {@code score}），不是分布
 * （分布是 C 端商品详情页要的，见 mall-bff）。两者刻意不对称，不要「顺手补齐」。</p>
 */
@Data
public class StoreEvaluationItemVO {

    /**
     * 评价 id（回复入口的路径标识）
     */
    private Long id;

    /**
     * 被评价的商品 SPU id
     */
    private Long spuId;

    /**
     * 商品名称（域侧按 {@code spuId} 批量反查当前商品名）。
     * <p>⚠ 商品已软删时域侧下发 {@code null}，本层填兜底文案——见
     * {@code StoreEvaluationBffService#SPU_NAME_FALLBACK}。</p>
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
     * 评价人展示名（customer-center 取不到资料时为占位「用户」）
     */
    private String nickname;

    /**
     * 评价人头像 URL（无头像 / 取不到资料时为 {@code null}，前端不渲染）
     */
    private String avatar;

    /**
     * 评价时间
     */
    private LocalDateTime createTime;

    /**
     * 商家回复内容（{@code null} = 未回复，页面显示「回复」入口）
     */
    private String replyContent;

    /**
     * 商家回复时间（{@code null} = 未回复）
     */
    private LocalDateTime replyTime;
}
