package com.panoramic.common.store.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺在售商品分页列表项 · <b>跨店通用</b>（admin BFF 管理视角 / mall-bff C 端视角共用；C 端输出前由 BFF 裁剪字段）
 * <p>比 owner 侧 {@link StoreGoodsSpuPageItemVO} 多出「所属店铺」与锁定明细，供平台查看与锁定操作。</p>
 * <p>字段来源：
 * <ul>
 *   <li>{@code storeName} 与全部 {@code lock*} 字段：由 store 域填充（域内可查 store_shop 与锁定列）；</li>
 *   <li>{@code categoryPath}：<b>由端 BFF 读时解析</b>（调 goods-center 批量路径接口）——域不持分类表；
 *       解析失败时留空，前端回退显示快照 {@code categoryName}。</li>
 * </ul></p>
 */
@Data
public class StoreGoodsSpuCrossShopPageItemVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 所属店铺名称（store 域填充，取 store_shop.shop_name）
     */
    private String storeName;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 所属分类 id（引用中台 goods_category）
     */
    private Long categoryId;

    /**
     * 分类名称快照（落库时的叶子分类名）
     */
    private String categoryName;

    /**
     * 分类全路径（如「服饰 / 男装 / T恤」），由端 BFF 读时解析；解析失败为空
     */
    private String categoryPath;

    /**
     * 品牌 id（引用中台 goods_brand）
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    private String brandName;

    /**
     * 上下架：0 下架，1 上架（由 SKU 联动推导）
     */
    private Integer shelfStatus;

    /**
     * SKU 数量
     */
    private Integer skuCount;

    /**
     * 在售 SKU 最低价（无上架 SKU 时为 null）
     */
    private BigDecimal minPrice;

    /**
     * 锁定状态：0 未锁定，1 已锁定
     */
    private Integer lockStatus;

    /**
     * 锁定原因（店铺端可展示，平台锁定必填）
     */
    private String lockReason;

    /**
     * 锁定人（UserType:UserId 原串，如 admin:1）。
     * <p>⚠ 仅管理端展示（前端渲染为「平台管理员(N)」）；店铺端不展示锁定人。</p>
     */
    private String lockUser;

    /**
     * 锁定时间
     */
    private LocalDateTime lockTime;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
