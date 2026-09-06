package com.panoramic.common.goods.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品（SPU）分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpuPageQueryDTO extends BasePageVO {

    /**
     * 分类ID（精确）
     */
    private Long categoryId;

    /**
     * 品牌ID（精确）
     */
    private Long brandId;

    /**
     * 展示状态：0 隐藏，1 展示
     */
    private Integer status;

    /**
     * 商品名称关键字（模糊匹配）
     */
    private String keyword;
}
