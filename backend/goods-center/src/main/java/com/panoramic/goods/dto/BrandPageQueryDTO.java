package com.panoramic.goods.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BrandPageQueryDTO extends BasePageVO {

    /**
     * 品牌名称关键字（模糊匹配）
     */
    private String keyword;
}
