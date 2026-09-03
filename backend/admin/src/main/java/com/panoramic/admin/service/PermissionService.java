package com.panoramic.admin.service;

import com.panoramic.admin.dto.PermissionSaveDTO;
import com.panoramic.admin.dto.PermissionUpdateDTO;
import com.panoramic.admin.vo.PermissionTreeVO;

import java.util.List;

/**
 * 权限服务
 */
public interface PermissionService {

    /**
     * 全量权限树（含按钮，权限管理页用）
     *
     * @return 权限树
     */
    List<PermissionTreeVO> tree();

    /**
     * 前端目录/菜单树（仅目录+页面，供前端目录接口）
     *
     * @return 目录树
     */
    List<PermissionTreeVO> menus();

    /**
     * 权限详情
     *
     * @param id 权限ID
     * @return 权限节点
     */
    PermissionTreeVO detail(Long id);

    /**
     * 新建权限
     *
     * @param dto 新建请求
     * @return 新权限ID
     */
    Long savePermission(PermissionSaveDTO dto);

    /**
     * 更新权限（仅名称/权限字符串/图标/排序）
     *
     * @param id  权限ID
     * @param dto 更新请求
     */
    void updatePermission(Long id, PermissionUpdateDTO dto);

    /**
     * 删除权限（存在子权限或已被角色引用时拒绝）
     *
     * @param id 权限ID
     */
    void deletePermission(Long id);
}
