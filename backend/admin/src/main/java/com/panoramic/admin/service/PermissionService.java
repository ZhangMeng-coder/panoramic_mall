package com.panoramic.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.admin.dto.PermissionSaveDTO;
import com.panoramic.admin.dto.PermissionUpdateDTO;
import com.panoramic.admin.entity.SysPermission;
import com.panoramic.admin.vo.PermissionTreeVO;

import java.util.Collection;
import java.util.List;

/**
 * 权限服务
 */
public interface PermissionService extends IService<SysPermission> {

    /**
     * 全量权限树（含按钮，权限管理页用）
     *
     * @return 权限树
     */
    List<PermissionTreeVO> tree();

    /**
     * 按角色过滤的菜单树（仅目录+页面，供登录用户侧边导航；会向上补全页面所属祖先目录保证树完整）。
     *
     * @param roleIds 当前登录用户拥有的角色ID集合（为空则返回空树）
     * @return 目录树
     */
    List<PermissionTreeVO> menusByRoleIds(List<Long> roleIds);

    /**
     * 取这些权限 ID 对应的权限字符串集合（仅取非空的 perms，登录用户权限快照用）
     *
     * @param permissionIds 权限ID集合，可为空/null
     * @return 去重后的权限字符串列表
     */
    List<String> permsOfIds(Collection<Long> permissionIds);

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

    /**
     * 校验权限 ID 是否全部存在（去重后比较数量；逻辑删除的自动被过滤）
     *
     * @param ids 权限ID集合，可为空/null
     * @return true=全部存在（或集合为空）
     */
    boolean existsAll(Collection<Long> ids);
}
