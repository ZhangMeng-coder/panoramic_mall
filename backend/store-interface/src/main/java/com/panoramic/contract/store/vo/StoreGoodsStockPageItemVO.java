package com.panoramic.contract.store.vo;

import com.panoramic.contract.store.dto.SpecAttr;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU 库存分页列表项（owner 侧：店铺端「库存管理」列表；store 域出参）。
 * <p>一行一条 SKU（商品名 / 规格 / 编码 / 价格 / 上下架 / 库存三列），
 * 供店主就地改库存与批量设库存。</p>
 */
@Data
public class StoreGoodsStockPageItemVO {

    /**
     * SKU 主键
     */
    private Long skuId;

    /**
     * 所属店铺商品 SPU id
     */
    private Long spuId;

    /**
     * 商品名称（所属 SPU 的名称）
     */
    private String spuName;

    /**
     * 商品主图 URL
     */
    private String mainImage;

    /**
     * 规格属性组合
     */
    private List<SpecAttr> specAttrs;

    /**
     * SKU 编码
     */
    private String skuCode;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 上下架：0 下架，1 上架
     */
    private Integer shelfStatus;

    /**
     * 总库存（商户维护）
     */
    private Integer stock;

    /**
     * 占用库存（交易域写入，本期恒 0）；只读展示，不参与本页编辑
     */
    private Integer lockedStock;

    /**
     * 低库存预警阈值（NULL = 不预警）
     */
    private Integer warnStock;
}
