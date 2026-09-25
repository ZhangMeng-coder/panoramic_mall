package com.panoramic.mallbff.dto;

import com.panoramic.common.vo.BasePageVO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评价分页查询参数（页面级，{@code POST /evaluations/page} 的入参）。
 *
 * <p>⚠ <b>只有 {@code spuId} 一个筛选维度</b>：C 端评价区永远长在<b>某个商品详情页</b>里，
 * 「看谁的」是路由带来的、不是页面可选项。⚠ <b>没有星级筛选</b>——C 端要的是星级<b>分布</b>
 * （{@code GET /evaluations/stat/{spuId}}），筛选是商户端的需求（两端刻意不对称：分布是展示、筛选是操作）。</p>
 *
 * <p>⚠ 每页条数由<b>页面传</b>（本层不写死 10）：域侧分页是通用能力，一页几条是页面的决定；
 * 域侧固定「时间倒序 + id 兜底」。
 * 分页字段继承自 {@link BasePageVO}，约束落在默认组——controller 里必须用<b>裸 {@code @Valid}</b>
 * （理由见 {@code CatalogController#goods}）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallEvaluationPageQueryDTO extends BasePageVO {

    /**
     * 被评价的商品 SPU id（<b>必填</b>：不传即等于放开成「全站评价」，与页面无此入口不符）
     */
    @NotNull(message = "商品ID不能为空")
    private Long spuId;
}
