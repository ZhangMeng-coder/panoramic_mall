package com.panoramic.mallbff.vo;

import com.panoramic.common.goods.dto.SpecAttr;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * C 端商品详情的 SKU 项（页面契约，见 docs/contracts/mall-bff.md）。
 * <p><b>只暴露顾客看得懂、且有权看的部分</b>：域出参 {@code StoreGoodsSkuVO} 还带
 * {@code spuId}（内部归属）、{@code skuCode}（店主内部编码）、{@code shelfStatus}
 * （上下架是内部状态，C 端只出上架项，不必再告诉顾客「这个是上架的」）。
 * 由 {@code CatalogBffService#toMallSku} 逐字段手工映射裁剪。</p>
 */
@Data
public class MallGoodsSkuVO {

    /**
     * SKU 主键（前端用它标识选中的规格组合）
     */
    private Long id;

    /**
     * 规格属性组合（如 [{spec:"颜色",value:"曜石黑"}]），页面按它做规格联动
     */
    private List<SpecAttr> specAttrs;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * SKU 图片 URL，空则由前端回退 CSS 渐变占位
     */
    private String mainImage;
}
