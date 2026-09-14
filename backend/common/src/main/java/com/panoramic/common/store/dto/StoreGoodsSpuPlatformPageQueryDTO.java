package com.panoramic.common.store.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 店铺在售商品分页查询参数 · platform 侧（全量，admin BFF 调用；store 域内部接口与 admin BFF 同源共享）
 * <p>与 owner 侧 {@link StoreGoodsSpuPageQueryDTO} 的区别：不带 store_id 约束（可由 {@code storeId} 筛选任意店）、
 * 多出「店铺筛选」与「锁定状态筛选」，且分类筛选是<b>多值</b>。</p>
 * <p><b>categoryIds 由端 BFF 做子树展开后传入</b>（域侧不持分类表，无法自行展开）：
 * 前端只传单个选中分类 id，admin BFF 取 goods-center 分类树递归收集该节点及其全部后代 id 组成本字段；
 * 域侧只做 {@code IN (categoryIds)} 过滤。空 / null 表示不按分类过滤。</p>
 * <p>⚠ 本 DTO 经 Feign 以 {@code POST + @RequestBody} 传输（不做 query string 序列化），
 * 以规避 {@code @SpringQueryMap} 对集合字段的序列化口径问题。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsSpuPlatformPageQueryDTO extends BasePageVO {

    /**
     * 关键字（模糊匹配商品名称）
     */
    private String keyword;

    /**
     * 分类筛选（多值，由端 BFF 子树展开后传入；空 = 不按分类过滤）
     */
    private List<Long> categoryIds;

    /**
     * 品牌筛选（引用中台品牌 id），空为全部
     */
    private Long brandId;

    /**
     * 店铺筛选（store_shop.id == 店主账号 id），空为全部店铺
     */
    private Long storeId;

    /**
     * 上下架筛选（0 下架 / 1 上架），空为全部
     */
    private Integer shelfStatus;

    /**
     * 锁定状态筛选（0 未锁定 / 1 已锁定），空为全部
     */
    private Integer lockStatus;
}
