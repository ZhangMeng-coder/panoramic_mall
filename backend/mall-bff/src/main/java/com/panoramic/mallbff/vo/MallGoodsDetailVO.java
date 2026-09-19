package com.panoramic.mallbff.vo;

import com.panoramic.contract.store.dto.SpecConfigItem;
import lombok.Data;

import java.util.List;

/**
 * C 端商品详情（页面契约，见 docs/contracts/mall-bff.md）。
 * <p><b>与域出参刻意解耦</b>：域侧的详情是「跨店通用 / 管理端超集」（{@code StoreGoodsSpuPlatformDetailVO}
 * 及其父类），含 {@code lockStatus}/{@code lockReason}/{@code lockUser}/{@code lockTime}（平台锁定）、
 * {@code goodsSpuId}/{@code centerVersion}（中台关联与版本戳）、{@code shelfStatus}（上下架内部状态）
 * 等管理端或内部字段——<b>域返回的字段不等于可以对外暴露</b>。
 * 由 {@code CatalogBffService#toMallDetail} 逐字段手工映射裁剪出本形状，域 VO 日后加字段不会自动漏到 C 端。</p>
 * <p><b>可见性口径与列表一致</b>（同一不变量）：上架 + 未被平台锁定 + 店铺已审核通过，否则接口回
 * 404「商品不存在或已下架」。故能拿到本对象的商品，一定也能在列表里被搜到。</p>
 * <p>⚠ {@code description} 是<b>店主自由录入的富文本</b>（库里按 HTML 存），出参前已在**本端 BFF
 * 出口**经 common 的 {@code HtmlSanitizer} 按白名单清洗（各端出口共用同一份，admin 端同字段同理）
 * ——前端按 HTML 渲染（{@code v-html}）是安全的。</p>
 */
@Data
public class MallGoodsDetailVO {

    /**
     * 店铺商品 id
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 主图 URL，空则由前端回退 CSS 渐变占位
     */
    private String mainImage;

    /**
     * 轮播图 URL 列表（可空）
     */
    private List<String> imageList;

    /**
     * 商品详情描述（店主录入的富文本，**已按白名单清洗**，前端可按 HTML 渲染）
     */
    private String description;

    /**
     * 规格属性配置。⚠ 页面**不再按它拼规格按钮**——它是 **SPU 级**配置，含只在已下架 SKU 上
     * 存在的值，拼出的组合可能在上架 SKU 里根本不存在（那正是旧版要靠「该组合暂未上架」
     * 兜底的原因）。现在页面改为**一行一个上架 SKU**，本字段只作为**维度顺序的基准**，
     * 供页面把一个 SKU 的 {@code specAttrs} 重排成稳定文案。
     */
    private List<SpecConfigItem> specConfig;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 所属店铺名称
     */
    private String storeName;

    /**
     * 所属分类 id
     */
    private Long categoryId;

    /**
     * 分类名称快照（域落库时的叶子分类名）
     */
    private String categoryName;

    /**
     * 品牌 id
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    private String brandName;

    /**
     * <b>上架</b> SKU 列表（下架 SKU 不下发——它不是「暂时缺货」，而是店主没在卖）。
     * <p>SPU 上架 ⟺ 至少一个 SKU 上架（域内不变量），故本列表必非空；
     * 价格区间由前端按它算（不另下发 min/max，免得同一条事实两处维护）。</p>
     */
    private List<MallGoodsSkuVO> skus;
}
