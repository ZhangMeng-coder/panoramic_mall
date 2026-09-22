package com.panoramic.contract.store.vo;

import com.panoramic.contract.store.dto.SpecAttr;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易侧 SKU 快照（下单流水线落订单明细快照用）。
 * <p>出参是 SKU 维度的一条快照，把「下单那一刻这一件长什么样、卖多少钱、还能不能买」一次性给全：
 * 商品与 SKU 的标识与展示信息、规格组合、价格、上下架 / 锁定 / 店铺审核状态、可用库存。</p>
 * <p><b>与 {@link StoreGoodsSkuVO} 的区别</b>：那个是商品详情里的 SKU 行（给页面看规格与库存），
 * 本 VO 是<b>交易撮合视角</b>——多带了归属于 SPU / 店铺的字段（{@code spuName} / {@code storeName} /
 * {@code spuShelfStatus} / {@code lockStatus} / {@code shopStatus}），
 * 因为下单要在一份数据里判「这个 SKU 到底能不能买」，不该让交易域再回查一次商品与店铺。</p>
 * <p>⚠ 本 VO <b>不做 C 端可见性裁剪、不判可购买性</b>：域只如实给出各维度的原始状态，
 * 「上架 + 未锁定 + 店铺已通过 + 库存够」这组判据在交易侧。域内不判身份、不做权限判断。</p>
 */
@Data
public class StoreGoodsSkuSnapshotVO {

    /**
     * SKU id
     */
    private Long skuId;

    /**
     * 所属店铺商品（SPU）id
     */
    private Long spuId;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 店铺名称（店铺行取不到时为空串）
     */
    private String storeName;

    /**
     * 商品名称（SPU.name；下单快照的商品名以它为准）
     */
    private String spuName;

    /**
     * 图片 URL：取 SKU 的 {@code mainImage}，为空时<b>回退 SPU 的 {@code mainImage}</b>
     */
    private String mainImage;

    /**
     * 规格属性组合（<b>已解析</b>的列表）。
     * <p>⚠ 库里的 {@code spec_attrs} 是 JSON 字符串，那是 store 的存储格式（实现细节）；
     * 本出参一律给解析后的结构，不让调用方去解析别域的存储格式。</p>
     */
    private List<SpecAttr> specAttrs;

    /**
     * SKU 售价
     */
    private BigDecimal price;

    /**
     * SKU 上下架：0 下架，1 上架（原值）
     */
    private Integer skuShelfStatus;

    /**
     * SPU 上下架：0 下架，1 上架（原值）
     */
    private Integer spuShelfStatus;

    /**
     * SPU 锁定状态：0 未锁定，1 已锁定（平台锁定；原值）
     */
    private Integer lockStatus;

    /**
     * 店铺审核状态：0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回。
     * <p>⚠ <b>店铺行取不到时为 {@code null}，不是 0</b>：0 是「草稿」这一真实状态，
     * 与「没有这一行」是两回事。交易侧据此判「店铺不可购买」，用 0 冒充会让缺失态被读成正常态。</p>
     */
    private Integer shopStatus;

    /**
     * 可用库存 = 库存表的 {@code stock}（{@code locked_stock} 已于 2026-09-21 废弃，不参与口径）。
     * <p>库存行缺失时按 <b>0</b> 计（与商品详情的 SKU 出参同口径：没库存行 = 可售 0 件）。</p>
     */
    private Integer availableStock;
}
