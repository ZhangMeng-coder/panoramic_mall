package com.panoramic.admin.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台「店铺商品管理」分页查询参数（页面入参，admin BFF 自有）。
 * <p>与 store 域入参 {@code StoreGoodsSpuPlatformPageQueryDTO} 的差别：本 DTO 面向页面，
 * 分类筛选是**单选** {@code categoryId}（前端级联选择器只回一个 id）；BFF 编排时取分类树
 * 递归展开成「该节点 + 全部后代」的 {@code categoryIds} 再传给域（A1：子树匹配）。
 * 域侧不持分类表，无法自行展开，故展开只能在 BFF 做。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopGoodsPageQueryDTO extends BasePageVO {

    /**
     * 关键字（模糊匹配商品名称）
     */
    private String keyword;

    /**
     * 分类筛选（单选；由 BFF 展开为「本节点 + 全部后代」后传给域）
     */
    private Long categoryId;

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
