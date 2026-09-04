package com.panoramic.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.admin.entity.SysRolePermission;

import java.util.Collection;
import java.util.List;

/**
 * 角色-权限关联服务（纯关联表，物理删除）
 */
public interface RolePermissionService extends IService<SysRolePermission> {

    /**
     * 查询某角色已分配的权限 ID 集合
     *
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    List<Long> permissionIdsByRoleId(Long roleId);

    /**
     * 按角色 ID 集合批量查询已分配的权限 ID（去重，菜单按角色过滤用）
     *
     * @param roleIds 角色ID集合
     * @return 权限ID列表（去重）
     */
    List<Long> permissionIdsByRoleIds(Collection<Long> roleIds);

    /**
     * 统计某权限被分配给了多少个角色（删除权限守卫用）
     *
     * @param permissionId 权限ID
     * @return 分配数
     */
    long countByPermissionId(Long permissionId);

    /**
     * 物理删除某角色的全部权限分配记录
     *
     * @param roleId 角色ID
     */
    void removeByRoleId(Long roleId);

    /**
     * 整体替换某角色的权限分配（先清旧再插新）
     *
     * @param roleId        角色ID
     * @param permissionIds 目标权限ID集合，可为空/null（清空）
     */
    void replaceByRole(Long roleId, Collection<Long> permissionIds);
}
