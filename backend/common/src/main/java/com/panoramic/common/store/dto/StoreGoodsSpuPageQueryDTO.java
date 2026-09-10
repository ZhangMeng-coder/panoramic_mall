package com.panoramic.common.store.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺在售商品分页查询参数（店主自己的商品，store 域内部接口与 store-bff 同源共享）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsSpuPageQueryDTO extends BasePageVO {

    /**
     * 关键字（模糊匹配商品名称）
     */
    private String keyword;

    /**
     * 分类筛选（引用中台分类 id），空为全部
     */
    private Long categoryId;

    /**
     * 品牌筛选（引用中台品牌 id），空为全部
     */
    private Long brandId;

    /**
     * 上下架筛选（0 下架 / 1 上架），空为全部
     */
    private Integer shelfStatus;
}
