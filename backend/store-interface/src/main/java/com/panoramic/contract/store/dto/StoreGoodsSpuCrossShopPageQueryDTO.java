package com.panoramic.contract.store.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 店铺在售商品分页查询参数 · <b>跨店通用</b>（无数据权限锚点；admin BFF 与 mall-bff 共用）
 * <p>与 owner 侧 {@link StoreGoodsSpuPageQueryDTO} 的区别：不带 store_id 约束（可由 {@code storeId} 筛选任意店）、
 * 多出「店铺审核状态筛选」「店铺筛选」与「锁定状态筛选」，分类与品牌筛选都是<b>多值</b>，
 * 且可指定排序。域侧只按传入条件过滤，<b>不含 C 端隐含约束</b>。</p>
 * <p><b>C 端（mall-bff）固定传 {@code shopStatus=2} + {@code shelfStatus=1} + {@code lockStatus=0}</b>
 * （只出「已审核通过店铺」的「在售且未锁定」商品）；管理端（admin BFF）不传这三个约束、走全量。
 * 两者的差别由各自端 BFF 传入的条件体现，域内不判身份、不做端别分流。</p>
 * <p><b>categoryIds 由端 BFF 做子树展开后传入</b>（域侧不持分类表，无法自行展开）：
 * 前端只传单个选中分类 id，端 BFF 取 goods-center 分类树递归收集该节点及其全部后代 id 组成本字段；
 * 域侧只做 {@code IN (categoryIds)} 过滤。空 / null 表示不按分类过滤。</p>
 * <p>⚠ 本 DTO 经 Feign 以 {@code POST + @RequestBody} 传输（不做 query string 序列化），
 * 以规避 {@code @SpringQueryMap} 对集合字段的序列化口径问题。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsSpuCrossShopPageQueryDTO extends BasePageVO {

    /**
     * 关键字（模糊匹配商品名称）
     */
    private String keyword;

    /**
     * 分类筛选（多值，由端 BFF 子树展开后传入；空 = 不按分类过滤）
     */
    private List<Long> categoryIds;

    /**
     * 品牌筛选（多值），空 = 不按品牌过滤
     */
    private List<Long> brandIds;

    /**
     * 所属店铺审核状态筛选（0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回），空 = 不过滤。
     * <p>C 端商城固定传 2（只出已审核通过店铺的商品）；管理端不传。</p>
     */
    private Integer shopStatus;

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

    /**
     * 排序：{@code default}（按 id 倒序，等价于原行为）/ {@code priceAsc} / {@code priceDesc}（按 min_price）。
     * <p>取其它值一律按 {@code default} 处理（宽松容错，不抛异常）。</p>
     */
    private String sort;
}
