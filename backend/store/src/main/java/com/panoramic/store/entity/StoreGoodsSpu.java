package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺在售商品（SPU）实体。
 * <p>字段与中台标准商品同构，但归属店铺（{@code store_id}，= 店主账号 id，账号店同 ID），
 * 且带「中台关联」（{@code goods_spu_id} + {@code center_version} 版本戳快照）与上下架状态。
 * 分类/品牌以「id 引用 + 名称快照」落库（下拉数据来自中台，保存时不再回查中台——「分类全路径」由端 BFF
 * 读取时另行调 goods-center 解析，域侧只提供 {@code category_id}）。</p>
 * <p>{@code shelf_status} 不独立可改：它由名下 SKU 联动推导，
 * 不变量为 {@code SPU上架 ⟺ ≥1 个 SKU 上架}（见 {@code StoreGoodsSpuServiceImpl} 的联动刷新）。</p>
 * <p><b>平台锁定</b>：{@code lock_status == 1} 表示被平台管理员锁定（见
 * {@code StoreGoodsSpuServiceImpl#lock}）。锁定会把名下 SKU 全部级联下架、SPU 随之推导为下架；
 * 锁定期 owner 侧整行只读（编辑/上下架/增删改 SKU/删除 全部拒绝）；解锁不自动恢复上架。
 * {@code lock_user} 是业务列（非审计列），存 {@code UserType:UserId} 原串（如 {@code admin:1}）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_spu")
public class StoreGoodsSpu extends BaseEntity {

    /** 上下架：下架 */
    public static final int SHELF_OFF = 0;
    /** 上下架：上架 */
    public static final int SHELF_ON = 1;

    /** 锁定状态：未锁定 */
    public static final int LOCK_OFF = 0;
    /** 锁定状态：已锁定（平台锁定） */
    public static final int LOCK_ON = 1;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 上次关联/同步时中台 SPU 的版本戳（null = 未关联中台）
     */
    private Long centerVersion;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 所属分类ID（引用中台 goods_category）
     */
    private Long categoryId;

    /**
     * 分类名称快照
     */
    private String categoryName;

    /**
     * 品牌ID（引用中台 goods_brand，可空）
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    private String brandName;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 轮播图 URL JSON 数组字符串（实体为 String，VO 层转 List）
     */
    private String imageList;

    /**
     * 商品详情（富文本 HTML）
     */
    private String description;

    /**
     * 规格属性配置 JSON 字符串（实体为 String，VO 层转 List）
     */
    private String specConfig;

    /**
     * 上下架：0 下架，1 上架（由 SKU 联动推导，不接受前端直接传入）
     */
    private Integer shelfStatus;

    /**
     * 锁定状态：0 未锁定，1 已锁定（平台锁定）
     */
    private Integer lockStatus;

    /**
     * 锁定原因（平台锁定时必填；店铺端「锁定信息」展示）
     */
    private String lockReason;

    /**
     * 锁定人（业务列，非审计列：UserType:UserId 原串，如 admin:1）。
     * 仅管理端展示，店铺端不展示（A2）。
     */
    private String lockUser;

    /**
     * 锁定时间
     */
    private LocalDateTime lockTime;

    /**
     * 在售（上架且未删）SKU 的最低价；无上架 SKU 时为 null。
     * <p>推导量，由 {@code StoreGoodsSpuServiceImpl#refreshMinPrice} 唯一写入，
     * 不接受外部直接赋值。用于 C 端列表展示「¥xx.xx 起」与价格排序。</p>
     */
    private BigDecimal minPrice;

    /**
     * 商品评分（冗余列）：该 SPU 全部评价的算术平均，保留 1 位小数；<b>null = 尚无评价</b>。
     * <p>推导量，由 {@code StoreGoodsSpuServiceImpl#updateScore} 唯一写入（调用方是评价服务，
     * 写入评价时在同一事务内重算），商品自身的写路径不得显式赋值。
     * 对外展示名就叫「评分」（商品列表 / 商品详情）。</p>
     */
    private BigDecimal score;
}
