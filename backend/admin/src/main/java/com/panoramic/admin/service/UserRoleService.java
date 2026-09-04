package com.panoramic.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.admin.entity.SysUserRole;

import java.util.Collection;
import java.util.List;

/**
 * 用户-角色关联服务（纯关联表，物理删除）
 */
public interface UserRoleService extends IService<SysUserRole> {

    /**
     * 按用户 ID 集合批量查询关联记录（用户分页角色徽标回填用）
     *
     * @param userIds 用户 ID 集合
     * @return 关联记录列表
     */
    List<SysUserRole> listByUserIds(Collection<Long> userIds);

    /**
     * 查询某用户已分配的角色 ID 集合
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> roleIdsByUserId(Long userId);

    /**
     * 查询某角色下已分配的用户 ID 集合
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    List<Long> userIdsByRoleId(Long roleId);

    /**
     * 统计某角色下已分配的用户数（删除角色守卫用）
     *
     * @param roleId 角色ID
     * @return 用户数
     */
    long countByRoleId(Long roleId);

    /**
     * 物理删除某用户的全部关联记录
     *
     * @param userId 用户ID
     */
    void removeByUserId(Long userId);

    /**
     * 物理删除某角色的全部关联记录
     *
     * @param roleId 角色ID
     */
    void removeByRoleId(Long roleId);

    /**
     * 整体替换某用户的角色分配（先清旧再插新）
     *
     * @param userId  用户ID
     * @param roleIds 目标角色ID集合，可为空/null（清空）
     */
    void replaceByUser(Long userId, Collection<Long> roleIds);

    /**
     * 整体替换某角色下的用户分配（先清旧再插新）
     *
     * @param roleId  角色ID
     * @param userIds 目标用户ID集合，可为空/null（清空）
     */
    void replaceByRole(Long roleId, Collection<Long> userIds);
}
