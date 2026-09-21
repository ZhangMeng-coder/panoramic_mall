package com.panoramic.mallbff.vo;

import com.panoramic.contract.store.dto.SpecAttr;
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
     * 规格属性组合（如 [{spec:"颜色",value:"曜石黑"}]）。
     * <p>页面**一行一个 SKU**，用它拼出行内规格文案（如「颜色：曜石黑 / 容量：256G」）；
     * 维度顺序由详情页的 {@code specConfig} 提供——库里 {@code spec_attrs} 是按提交顺序
     * 原样存的，各 SKU 未必一致，不重排会出现一行「颜色/容量」另一行「容量/颜色」。</p>
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

    /**
     * 可用库存（= 域侧 {@code availableStock} = {@code stock}；{@code locked_stock} 已废弃、不参与口径）；
     * 0 表示该规格已售罄。
     * <p>⚠ 库存<b>不参与详情可见性</b>：售罄商品照常可打开，只是选中该规格时展示「已售罄」。
     * 阈值的 {@code warn_stock} 是商户端内部信息，不进 C 端。</p>
     */
    private Integer availableStock;
}
