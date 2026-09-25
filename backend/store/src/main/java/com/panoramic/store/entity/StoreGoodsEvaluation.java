package com.panoramic.store.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商品评价实体（store 域）。
 * <p>评价挂在<b>商品 SPU</b> 上（{@code order_no + spu_id} 唯一），但带 <b>SKU 快照</b>：
 * 同一订单里同一 SPU 下的多个 SKU 合成一条评价，{@code sku_snapshot}（JSON 数组）存下单时
 * 该 SPU 的全部 SKU 行组（规格 / 单价 / 数量）。</p>
 * <p>{@code store_id} 是<b>冗余列</b>（可由 {@code spu_id} 反查），冗余的理由是两条查询都按它做：
 * ① 店铺评分的聚合；② 商户端「我店铺的评价」分页与按店筛选。反查口径见
 * {@code StoreGoodsSpuService#findStoreIdBySpuId}（⚠ <b>不过滤逻辑删除</b>）。</p>
 * <p><b>回复就地一列</b>：一条评价至多一条回复（{@code reply_content} + {@code reply_time}），
 * 故不另立回复表，也没有「改回复 / 删回复」的入口。</p>
 * <p>{@code sku_snapshot} 是 JSON 列，<b>实体为 String、VO 层转 List</b>（与 {@link StoreGoodsSpu} 的
 * {@code image_list} / {@code spec_config} 同一手法）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_goods_evaluation")
public class StoreGoodsEvaluation extends BaseEntity {

    /** 评分下限（星） */
    public static final int SCORE_MIN = 1;
    /** 评分上限（星） */
    public static final int SCORE_MAX = 5;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号（评价归属的完成态订单）
     */
    private String orderNo;

    /**
     * 被评价的商品 SPU id（store_goods_spu.id）
     */
    private Long spuId;

    /**
     * 所属店铺 id（= 店主账号 id；由 spu_id 反查落库，冗余以便按店聚合 / 筛选）
     */
    private Long storeId;

    /**
     * 评价人（= mall_user.id）
     */
    private Long customerId;

    /**
     * 评分：1 ~ 5 星（整星）
     */
    private Integer score;

    /**
     * 评价文字（可空）
     */
    private String content;

    /**
     * SKU 快照 JSON 数组字符串（实体为 String，VO 层转 List）
     */
    private String skuSnapshot;

    /**
     * 商家回复内容（null = 未回复）
     */
    private String replyContent;

    /**
     * 商家回复时间（null = 未回复）
     */
    private LocalDateTime replyTime;
}
