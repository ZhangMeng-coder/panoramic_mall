package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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

    /** 待重算标记：已收敛（排序分与三个维度一致，无需重算） */
    public static final int RANK_DIRTY_OFF = 0;
    /** 待重算标记：待重算（三个维度之一在置脏之后又变过，排序分可能已过期） */
    public static final int RANK_DIRTY_ON = 1;

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

    /**
     * 累计销量：该 SPU 在**已完成**订单里的合计件数（只加不减）。
     * <p>事实源不在这里——它是 {@code store_goods_sales_log} 的汇总冗余列，由
     * {@code StoreGoodsSalesLogService#pushSales}（域间推送入口）在记台账的同一事务内自增；
     * 商品自身的写路径不得赋值。</p>
     * <p>⚠ <b>口径是「订单完成」（{@code OrderStatus.RECEIVED}），不是支付</b>；且**没有回退通路**
     * ——收货是主链终点，已完成的单不能再取消 / 退款，故无需减。</p>
     * <p>⚠ {@code updateStrategy = NEVER}（本列与下面三个推导列同）：三种触发点都靠显式
     * {@code lambdaUpdate().set(...)} 写，而 {@code updateById(entity)} 走的是 MP 默认的 {@code NOT_NULL}
     * 策略——非空字段一律进 {@code SET}。商品编辑 / 上下架联动都是「读回整行 → 改业务字段 → 写回」，
     * 那个快照里的本列是**读的那一刻**的旧值，写回即把并发自增的销量**静默抹回旧值**（销量少记、
     * 且没有任何报错）。加了它，实体写路径碰不到这四列——不是我漏了，是刻意让它们只能被显式写。</p>
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer salesCount;

    /**
     * 排序分（0.00 ~ 100.00，越高越靠前）：评分 / 销量 / 价格三维度的加权和。
     * <p>推导量，由 {@code StoreGoodsSpuServiceImpl#recalculateRank} 唯一写入（算分口径见
     * {@code rank/RankCalculator} 与 store README）。<b>纯内部排序量，不进任何 VO</b>——
     * C 端列表用它排序，但不把分数下发给页面。</p>
     * <p>⚠ {@code updateStrategy = NEVER}：理由同 {@link #salesCount}——写回旧快照会把刚算出的新分抹掉。</p>
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private BigDecimal rankScore;

    /**
     * 待重算标记：1 = 该 SPU 的排序分可能已过期，等待异步重算；0 = 已收敛。
     * <p>由 {@code StoreGoodsSpuServiceImpl#markRankDirty} 置 1（三个触发点：价格变 / 评分变 / 销量变），
     * 由 {@code #recalculateRank} 清 0。⚠ <b>它是「兜底重算」的扫描条件</b>：异步任务失败 / 进程重启 /
     * 线程池打满时，这个标记是最后一次重算机会的凭据（见 store README 的最终一致口径）。</p>
     * <p>⚠ {@code updateStrategy = NEVER}：写回旧快照会把 {@code markRankDirty} 刚置上的 1 **清回 0**
     * ——该商品从此再也进不了兜底扫描，永久停在旧分上（这正是脏标记要防的静默错）。</p>
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer rankDirty;

    /**
     * 排序分版本号：每次置脏自增，用于**重算结果的新旧判定**。
     * <p>算分与清脏在一条 UPDATE 里完成，条件是 {@code rank_version} 与读取时相等
     * （{@code where id = ? and rank_version = ?}）：不相等说明读完之后又有新变更、算出的分已经过期，
     * 此时**不清脏**（脏标记已由那次变更置 1），交给下一轮重算。</p>
     * <p>⚠ 只有这一处会自增（{@code markRankDirty}）；重算侧只读不写。若不用版本号，就只有
     * 「先算后写」的竞态：并发变更下会把脏标记连同过期的分一起落库，该商品此后**永远**停在旧分上。</p>
     * <p>⚠ {@code updateStrategy = NEVER}：写回旧快照会把版本号**倒退**，版本守卫随即失效——
     * 在途重算拿旧版本比对会意外命中，把过期的分当成新分落库（守卫形同虚设）。</p>
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer rankVersion;
}
