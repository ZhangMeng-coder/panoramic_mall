package com.panoramic.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车行实体（一个顾客多行，主键自增；一行 = 一个 SKU）。
 * <p>⚠ 主键 {@code IdType.AUTO}：{@code trade_cart_item.id} 是 {@code AUTO_INCREMENT}，由 DB 生成后回填。</p>
 * <p>归属锚点 {@code customerId} = {@code mall_user.id}（跨域 id 引用、无外键）：本域所有读写一律以
 * 「id + customerId」双条件限定作用域，域内不做身份判断（防线在 mall-bff，见 docs/contracts/trade-center.md）。
 * {@code spuId} / {@code skuId} 是 store 域的引用，本域**不校验其存在性**、也不查商品（购物车不是商品快照）。</p>
 * <p>⚠ {@code (customerId, skuId)} 是**唯一键**，且本表**只走物理删除**（故 {@code is_delete} 恒 0，
 * 该列只是为满足 {@link BaseEntity} 而保留）——理由见 {@code TradeCartItemMapper} 的类注释。
 * 由此推出两条口径：同一 SKU 在车里**至多一行**（重复加购是累加数量），以及
 * <b>「插入撞唯一键」是可以预期的正常分支</b>（由 service 回落为原子自增，不是异常路径）。</p>
 * <p>⚠ {@code selected} 用 {@code Integer}（0/1）而非 {@code Boolean}：库列是 {@code TINYINT}，
 * 与库内其它开关列（{@code customer_address.is_default} / {@code store_goods_spu.lock_status} …）同口径，
 * 实体层不引入 Boolean↔TINYINT 的驱动侧隐式映射；对外的 {@code TradeCartItemVO} 才转成 {@code Boolean}。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本实体与 service **不显式赋值**（Global Constraint 3）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_cart_item")
public class TradeCartItem extends BaseEntity {

    /**
     * 主键（IdType.AUTO：由 DB 自增生成）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属顾客（= mall_user.id，数据权限锚点）
     */
    private Long customerId;

    /**
     * 店铺商品 SPU id（store 域；本域只记引用，不校验、不联查）
     */
    private Long spuId;

    /**
     * 店铺商品 SKU id（store 域；与 customerId 一起构成唯一键 uk_customer_sku）
     */
    private Long skuId;

    /**
     * 数量：1..999（上限由 DTO 的 @Max 与自增 SQL 的 LEAST(...,999) 两条路径分别封顶）
     */
    private Integer quantity;

    /**
     * 选中状态：0未选，1选中（加入即选中：新行默认 1；服务端持久化，不是前端本地态）
     */
    private Integer selected;
}
