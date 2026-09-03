package com.panoramic.admin.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQueryDTO extends BasePageVO {

    /**
     * 关键字（角色名称/标识模糊匹配）
     */
    private String keyword;
}
