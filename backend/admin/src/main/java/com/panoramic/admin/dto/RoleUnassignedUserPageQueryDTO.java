package com.panoramic.admin.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色下“未分配用户”分页查询参数
 * <p>分配页面展示的是不在该角色内的用户，故可分配给该角色。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoleUnassignedUserPageQueryDTO extends BasePageVO {

    /**
     * 关键字（用户名/昵称/手机号模糊匹配）
     */
    private String keyword;
}
