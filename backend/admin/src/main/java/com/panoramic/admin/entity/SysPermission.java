package com.panoramic.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限实体（目录/页面/按钮树）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 父权限ID，0 表示顶级（仅目录）
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
     * 页面路由地址（仅 type=2 页面填写，如 /user，前端菜单据此导航）
     */
    private String route;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}
