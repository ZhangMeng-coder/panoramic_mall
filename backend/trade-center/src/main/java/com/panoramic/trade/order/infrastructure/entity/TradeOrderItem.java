package com.panoramic.trade.order.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 订单明细实体（一行 = 一个 SKU；快照字段下单即冻结，此后商品改名换图不影响已有订单）。
 * <p>⚠ {@code specAttrs} 用 {@code String} 映射 JSON 列（口径同 store 域的 {@code StoreGoodsSku.specAttrs}），
 * 不用 {@code Map}；无规格写 {@code {}}。</p>
 * <p>⚠ 本表 {@code is_delete} 恒 0：明细不删行，该列只为对齐 {@link BaseEntity} 而保留。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本实体不显式赋值。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order_item")
public class TradeOrderItem extends BaseEntity {

    /**
     * 主键（IdType.AUTO：由 DB 自增生成）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * trade_order.order_no
     */
    private String orderNo;

    /**
     * 店铺商品 SPU id（store 域）
     */
    private Long spuId;

    /**
     * 店铺商品 SKU id（store 域）
     */
    private Long skuId;

    /**
     * 下单时冻结的商品名
     */
    private String goodsName;

    /**
     * 下单时冻结的主图 URL
     */
    private String mainImage;

    /**
     * 下单时冻结的规格属性（键值对 JSON，无规格写 {}）
     */
    private String specAttrs;

    /**
     * 下单时单价（≥0.01）
     */
    private BigDecimal unitPrice;

    /**
     * 数量：1..999
     */
    private Integer quantity;

    /**
     * 小计（= 单价×数量，两位小数四舍五入）
     */
    private BigDecimal subtotal;
}
