package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺在售商品 SKU 库存实体（与 {@link StoreGoodsSku} 1:1，独立成表）。
 * <p><b>独立成表的理由</b>：库存若加成 {@code store_goods_sku} 的一列，改库存的 {@code UPDATE}
 * 会锁住该 SKU 行，而商品编辑 / 上下架 / 平台锁定 / 派生量刷新都在改同一行 ——
 * 补货会与商品运维互相阻塞。独立后库存行的写锁只覆盖库存行本身（R14）。</p>
 * <p>不冗余 {@code store_id} / {@code spu_id}：归属链
 * {@code sku_id → store_goods_sku.spu_id → store_goods_spu.store_id}，
 * 归属与平台锁定校验由调用方（{@code StoreGoodsSpuServiceImpl}）负责，本实体不持别的表。</p>
 * <p>可用库存 = {@code stock}；C 端展示的一律是可用库存。{@code warn_stock}
 * 仅商户端低库存预警用（NULL = 不预警），不进 C 端。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_sku_stock")
public class StoreGoodsSkuStock extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * store_goods_sku.id（一 SKU 一行，唯一键）
     */
    private Long skuId;

    /**
     * 总库存（商户维护）；可用库存 = stock
     */
    private Integer stock;

    /**
     * <b>已废弃</b>（2026-09-21 裁定）：不参与可用库存口径、不得新增写入；该列是先前自加的预留，
     * 流程不清晰、回冲操作繁琐。DDL 删列见仓库根 todo.md「残留 / 后续」第 1 行。
     */
    @Deprecated
    private Integer lockedStock;

    /**
     * 低库存预警阈值（NULL = 不预警，仅商户端用）
     */
    private Integer warnStock;
}
