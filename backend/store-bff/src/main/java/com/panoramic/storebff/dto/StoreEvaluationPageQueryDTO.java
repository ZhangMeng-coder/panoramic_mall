package com.panoramic.storebff.dto;

import com.panoramic.common.vo.BasePageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺端 BFF · 本店评价分页查询参数（<b>页面入参</b>，本层私有）。
 *
 * <p>⚠ <b>没有 {@code storeId} 字段</b>（cross-cutting 第 22 条）：作用域在
 * {@code StoreEvaluationBffService} 里被<b>无条件</b>写成登录态（店主侧只有「本店评价」一个视角）。
 * 页面能传来的锚点等于把数据权限交给页面。</p>
 *
 * <p>⚠ <b>与 C 端刻意不对称</b>：商户端<b>要星级筛选、不要星级分布</b>
 * （C 端要分布、不要筛选）——分布是展示、筛选是操作，商户按星级翻差评才是刚需。</p>
 *
 * <p>⚠ <b>星级是单值而不是集合</b>：页面是「全部 / 5 星 / …」<b>单选</b>，故页面契约只收一个
 * {@code score}；<b>域侧</b>入参是集合（{@code scores}，非空即 {@code IN} 过滤），由本层转成单元素集合。
 * 这样做的两个理由：① 页面契约如实反映页面的操作形态；② 本接口是 {@code GET}，
 * 把集合塞进 query string 会被 axios 序列化成 {@code scores[]=5} 形状（本仓库为此类入参一律改走
 * {@code POST + body}，见 store 域 {@code StoreGoodsEvaluationPageQueryDTO}）。
 * 将来页面改多选时，本类换成 {@code List<Integer>} 即可，<b>域侧不用动</b>。</p>
 *
 * <p>⚠ 分页字段（{@code pageNum} / {@code pageSize}）继承自 {@link BasePageVO}，
 * 约束落在默认组——controller 里用 {@code @Validated} 触发（与本模块 GET 分页入参同款）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreEvaluationPageQueryDTO extends BasePageVO {

    /**
     * 商品 SPU id（按商品筛选）；不填则不筛
     * <p>商品选择器复用<b>既有</b>在售商品分页（{@code GET /goods/spu/page}），不为它新造一套。</p>
     */
    private Long spuId;

    /**
     * 星级筛选（用户可读的口径：1~5 星；不填 = 全部）
     */
    @Min(value = 1, message = "星级取值不正确")
    @Max(value = 5, message = "星级取值不正确")
    private Integer score;
}
