package com.panoramic.common.store.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺在售商品分页列表项（owner 侧：店铺端「我的商品」列表；store 域出参）
 * <p>分类/品牌<b>名称</b>取落库时的快照，不回查中台；分类<b>全路径</b>（{@code categoryPath}）
 * 由端 BFF 读时补全（域不持分类表）——两者并存，路径解析失败时前端回退显示快照名。</p>
 * <p>锁定字段由域填充，owner 端只读展示（不展示锁定人）。</p>
 */
@Data
public class StoreGoodsSpuPageItemVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 分类 id（端 BFF 读时据此解析全路径）
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
     * 品牌名称快照
     */
    private String brandName;

    /**
     * 上下架：0 下架，1 上架
     */
    private Integer shelfStatus;

    /**
     * 锁定状态：0 未锁定，1 已锁定（平台锁定）
     */
    private Integer lockStatus;

    /**
     * 锁定原因（锁定信息展示，不含锁定人）
     */
    private String lockReason;

    /**
     * 锁定人（UserType:UserId 原串，如 admin:1）。
     * <p>⚠ 域照常返回（详情/列表同源），但店铺端<b>不展示锁定人</b>——由前端不渲染实现，不下沉到文案。</p>
     */
    private String lockUser;

    /**
     * 锁定时间
     */
    private LocalDateTime lockTime;

    /**
     * SKU 数量
     */
    private Integer skuCount;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
