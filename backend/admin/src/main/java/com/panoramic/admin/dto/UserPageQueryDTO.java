package com.panoramic.admin.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQueryDTO extends BasePageVO {

    /**
     * 关键字（用户名/昵称/手机号模糊匹配）
     */
    private String keyword;

    /**
     * 状态：1 启用，0 停用（为空查询全部）
     */
    private Integer status;
}
