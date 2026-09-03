package com.panoramic.admin.vo;

import lombok.Data;

import java.util.List;

/**
 * 权限树节点响应
 */
@Data
public class PermissionTreeVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 父权限ID，0 表示顶级
     */
    private Long parentId;

    /**
     * 权限名称（菜单/按钮名）
     */
    private String name;

    /**
     * 类型：1=目录，2=页面，3=按钮
     */
    private Integer type;

    /**
     * 权限字符串，如 system:user:list
     */
    private String perms;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 子权限列表
     */
    private List<PermissionTreeVO> children;
}
