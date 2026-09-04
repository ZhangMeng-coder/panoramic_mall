package com.panoramic.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.admin.dto.RolePageQueryDTO;
import com.panoramic.admin.dto.RoleSaveDTO;
import com.panoramic.admin.dto.RoleUpdateDTO;
import com.panoramic.admin.entity.SysRole;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.RoleVO;

import java.util.Collection;
import java.util.List;

/**
 * 角色服务
 */
public interface RoleService extends IService<SysRole> {

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
     * 给角色分配用户（整体替换）
     *
     * @param roleId  角色ID
     * @param userIds 目标用户ID集合，可为空（清空）
     */
    void assignUsers(Long roleId, List<Long> userIds);

    /**
     * 按 ID 集合查询并转 VO（按 sort/id 升序排序，供用户侧角色徽标回填；逻辑删除的自动被过滤）
     *
     * @param ids 角色ID集合，可为空/null
     * @return 排序后的角色VO列表
     */
    List<RoleVO> listVOsByIdsSorted(Collection<Long> ids);

    /**
     * 校验角色 ID 是否全部存在（去重后比较数量；逻辑删除的自动被过滤）
     *
     * @param ids 角色ID集合，可为空/null
     * @return true=全部存在（或集合为空）
     */
    boolean existsAll(Collection<Long> ids);
}
