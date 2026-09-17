package com.panoramic.mallbff.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * C 端商品分页查询参数（页面契约，见 docs/contracts/mall-bff.md）。
 * <p>本 DTO 是<b>页面入参</b>，与域侧 {@code StoreGoodsSpuCrossShopPageQueryDTO} 刻意分开：
 * 页面只表达「用户选了什么」，C 端专属约束（{@code shopStatus=2} + {@code shelfStatus=1}
 * + {@code lockStatus=0}）与分类子树展开由 {@code CatalogBffService} 在调域前补上，
 * 前端无法（也不应）左右展示口径。</p>
 * <p>⚠ 分类筛选传的是<b>单个锚点 + 多选</b>，不是「已展开的子树 id」：子树展开要读商品域
 * 分类树，那是 BFF 的职责（域不持分类表）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallGoodsPageQueryDTO extends BasePageVO {

    /**
     * 关键字（模糊匹配商品名称）
     */
    private String keyword;

    /**
     * 分类页路由锚点（该分类 + 全部后代），搜索页为空
     */
    private Long categoryId;

    /**
     * 已选分类筛选（多选；各自展开子树后取并集），空 = 不按分类过滤
     */
    private List<Long> categoryIds;

    /**
     * 已选品牌筛选（多选），空 = 不按品牌过滤
     */
    private List<Long> brandIds;

    /**
     * 排序：default / priceAsc / priceDesc
     */
    private String sort;
}
