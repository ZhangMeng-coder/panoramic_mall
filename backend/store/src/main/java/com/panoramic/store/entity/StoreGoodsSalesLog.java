package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺商品销量台账实体（订单完成时入账，**只增不改、只加不减**）。
 * <p>表的语义是「<b>这笔单的这个商品，销量已经算过了</b>」——一行即一次入账，故
 * {@code (order_no, spu_id)} 是唯一键，也是<b>推送幂等</b>的落库兜底（补推会重放同一笔）。</p>
 * <p>{@code store_goods_spu.sales_count} 是这份台账的**汇总冗余列**：入账时先把台账行插进去，
 * 再在同一个事务里自增计数——计数错了可以拿台账重算，台账行丢了就是事实丢了。</p>
 * <p>⚠ <b>与 {@code store_goods_sku_stock_log} 刻意不同构</b>：那张表有 {@code kind}（OUT / REVERT
 * 两个方向），本表<b>只有入账一个方向</b>——销量口径是「订单完成」，而已完成的订单不能再取消 / 退款
 * （{@code markCancelled} 的来源是待支付、{@code markRefunded} 的来源是已支付），故没有回退通路。
 * 若照抄那张表加一个恒为 ADD 的 {@code kind}，等于给一个不存在的场景留一列。</p>
 * <p>⚠ <b>不设业务发生时间列</b>（不同于库存流水的 {@code occurred_at}）：入账时点由调用方在订单完成时
 * 立即推送，审计列 {@code create_time} 即入账时间；补推的延迟只影响这一列的精度，而本表的对账主键
 * 是 {@code (order_no, spu_id)}，不需要业务时间。</p>
 * <p>审计列（create_user / create_time / update_user / update_time）由 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本类与写它的 service 一律不手写。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_sales_log")
public class StoreGoodsSalesLog extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号（入账来源；与 SPU 一起构成唯一键，幂等按它判）
     */
    private String orderNo;

    /**
     * store_goods_spu.id（被计入销量的店铺商品）
     */
    private Long spuId;

    /**
     * 入账件数（恒正，见 {@code StoreGoodsSalesItemDTO}）
     */
    private Integer quantity;
}
