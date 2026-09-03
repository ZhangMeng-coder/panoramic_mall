package com.panoramic.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色响应
 */
@Data
public class RoleVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色标识（如 admin）
     */
    private String code;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
