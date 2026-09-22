package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 店铺在售商品 SKU 库存变动流水实体（出库 / 回补）。
 * <p><b>只增不改</b>：这是历史事实表，落库后不修正、不删除——错了用反向流水抵消。
 * 故 {@link com.panoramic.store.service.StoreGoodsSkuStockLogService} 只暴露「记一条」与两个查询，
 * 不提供任何 update / remove 业务入口（继承自 MyBatis-Plus 的基类能力同样不外扩）。</p>
 * <p>库存表 {@code store_goods_sku_stock} 仍是<b>唯一事实源</b>：扣减/回补都先在库存表上做
 * 原子条件更新，本表只记录「谁在何时因哪张单变了多少」——影响行数才是扣减的判据，本表不参与判据。</p>
 * <p>{@code change_quantity} 恒正，方向由 {@code kind} 表达（{@link #KIND_OUT} / {@link #KIND_REVERT}）：
 * 存负数会让「扣了多少 / 补了多少」两个方向的读法不一致。{@code occurred_at} 是<b>业务发生时间</b>
 * （由写方带入），与审计列 {@code create_time}（落库时间）刻意分开。</p>
 * <p>审计列（create_user / create_time / update_user / update_time）由 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本类与写它的 service 一律不手写。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_sku_stock_log")
public class StoreGoodsSkuStockLog extends BaseEntity {

    /** 变动方向：扣减（下单出库） */
    public static final String KIND_OUT = "OUT";
    /** 变动方向：回补（取消/超时/失败后归还库存） */
    public static final String KIND_REVERT = "REVERT";

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * store_goods_sku.id
     */
    private Long skuId;

    /**
     * 订单号（扣减与回补靠它配对，回补幂等也按它判）
     */
    private String orderNo;

    /**
     * 变动方向：{@link #KIND_OUT} 扣减 / {@link #KIND_REVERT} 回补
     */
    private String kind;

    /**
     * 变动数量（恒正，方向由 {@code kind} 表达）
     */
    private Integer changeQuantity;

    /**
     * 变动发生时间（业务时间，由写方带入）
     */
    private LocalDateTime occurredAt;
}
