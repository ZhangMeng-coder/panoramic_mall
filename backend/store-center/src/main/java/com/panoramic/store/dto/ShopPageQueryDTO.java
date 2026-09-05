package com.panoramic.store.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺分页查询参数（平台店铺列表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ShopPageQueryDTO extends BasePageVO {

    /**
     * 审核状态筛选（0草稿/1待审核/2已通过/3已驳回），空为全部
     */
    private Integer status;

    /**
     * 关键字（模糊匹配店铺名称/联系人/营业执照企业名/信用代码）
     */
    private String keyword;
}
