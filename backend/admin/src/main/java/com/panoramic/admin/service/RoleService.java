package com.panoramic.admin.service;

import com.panoramic.admin.dto.RolePageQueryDTO;
import com.panoramic.admin.dto.RoleSaveDTO;
import com.panoramic.admin.dto.RoleUnassignedUserPageQueryDTO;
import com.panoramic.admin.dto.RoleUpdateDTO;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.RoleVO;
import com.panoramic.admin.vo.UserVO;

import java.util.List;

/**
 * 角色服务
 */
public interface RoleService {

    /**
     * 角色分页查询
     *
     * @param dto 分页查询参数（名称/标识关键字模糊匹配）
     * @return 分页结果
     */
    PageResult<RoleVO> page(RolePageQueryDTO dto);

    /**
     * 全量角色列表（下拉选择用）
     *
     * @return 角色列表
     */
    List<RoleVO> listAll();

    /**
     * 角色详情
     *
     * @param id 角色ID
     * @return 角色响应
     */
    RoleVO detail(Long id);

    /**
     * 新建角色
     *
     * @param dto 新建请求
     * @return 新角色ID
     */
    Long saveRole(RoleSaveDTO dto);

    /**
     * 更新角色
     *
     * @param id  角色ID
     * @param dto 更新请求
     */
    void updateRole(Long id, RoleUpdateDTO dto);

    /**
     * 删除角色（已分配给用户时拒绝；仅有关联权限时连同关系一起清理）
     *
     * @param id 角色ID
     */
    void deleteRole(Long id);

    /**
     * 查询角色已分配的权限ID集合
     *
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    List<Long> getRolePermissionIds(Long roleId);

    /**
     * 给角色分配权限（整体替换）
     *
     * @param roleId        角色ID
     * @param permissionIds 目标权限ID集合，可为空（清空）
     */
    void assignPermissions(Long roleId, List<Long> permissionIds);

    /**
     * 查询角色下已分配的用户ID集合
     *
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    List<Long> getUserIds(Long roleId);

    /**
     * 分页查询“不在该角色内”的用户（分配用户页面用）
     *
     * @param roleId 角色ID
     * @param dto    分页查询参数（关键字）
     * @return 分页结果
     */
    PageResult<UserVO> unassignedUsersPage(Long roleId, RoleUnassignedUserPageQueryDTO dto);

    /**
     * 给角色分配用户（整体替换）
     *
     * @param roleId  角色ID
     * @param userIds 目标用户ID集合，可为空（清空）
     */
    void assignUsers(Long roleId, List<Long> userIds);
}
