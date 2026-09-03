package com.panoramic.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.panoramic.admin.dto.RolePageQueryDTO;
import com.panoramic.admin.dto.RoleSaveDTO;
import com.panoramic.admin.dto.RoleUnassignedUserPageQueryDTO;
import com.panoramic.admin.dto.RoleUpdateDTO;
import com.panoramic.admin.entity.SysPermission;
import com.panoramic.admin.entity.SysRole;
import com.panoramic.admin.entity.SysRolePermission;
import com.panoramic.admin.entity.SysUser;
import com.panoramic.admin.entity.SysUserRole;
import com.panoramic.admin.mapper.SysPermissionMapper;
import com.panoramic.admin.mapper.SysRoleMapper;
import com.panoramic.admin.mapper.SysRolePermissionMapper;
import com.panoramic.admin.mapper.SysUserMapper;
import com.panoramic.admin.mapper.SysUserRoleMapper;
import com.panoramic.admin.service.RoleService;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.RoleVO;
import com.panoramic.admin.vo.UserVO;
import com.panoramic.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public PageResult<RoleVO> page(RolePageQueryDTO dto) {
        Page<SysRole> rolePage = dto.toPage(SysRole.class);
        IPage<SysRole> result = roleMapper.selectPage(rolePage,
                Wrappers.<SysRole>lambdaQuery()
                        .and(StringUtils.hasText(dto.getKeyword()), w -> w
                                .like(SysRole::getName, dto.getKeyword())
                                .or().like(SysRole::getCode, dto.getKeyword()))
                        .orderByAsc(SysRole::getSort)
                        .orderByDesc(SysRole::getId));

        List<RoleVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public List<RoleVO> listAll() {
        return roleMapper.selectList(
                        Wrappers.<SysRole>lambdaQuery()
                                .orderByAsc(SysRole::getSort)
                                .orderByAsc(SysRole::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public RoleVO detail(Long id) {
        return toVO(getByIdOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveRole(RoleSaveDTO dto) {
        checkNameDuplicate(dto.getName(), null);
        checkCodeDuplicate(dto.getCode(), null);
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role);
        if (role.getSort() == null) {
            role.setSort(0);
        }
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleUpdateDTO dto) {
        SysRole role = getByIdOrThrow(id);
        checkNameDuplicate(dto.getName(), id);
        checkCodeDuplicate(dto.getCode(), id);
        BeanUtils.copyProperties(dto, role);
        if (role.getSort() == null) {
            role.setSort(0);
        }
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        getByIdOrThrow(id);
        // 已分配给用户 → 拒绝
        Long userCount = userRoleMapper.selectCount(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
        if (userCount > 0) {
            throw new ServiceException("该角色已分配给用户，无法删除");
        }
        // 存在权限分配关系 → 连带清理关系后再删除角色
        rolePermissionMapper.delete(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        getByIdOrThrow(roleId);
        List<SysRolePermission> relations = rolePermissionMapper.selectList(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        getByIdOrThrow(roleId);
        // 去重并校验权限全部存在
        List<Long> distinctPermissionIds = permissionIds.stream().distinct().collect(Collectors.toList());
        checkPermissionIdsExist(distinctPermissionIds);
        // 整体替换：先清旧，再插新
        rolePermissionMapper.delete(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId));
        if (!distinctPermissionIds.isEmpty()) {
            for (Long permissionId : distinctPermissionIds) {
                SysRolePermission relation = new SysRolePermission();
                relation.setRoleId(roleId);
                relation.setPermissionId(permissionId);
                rolePermissionMapper.insert(relation);
            }
        }
    }

    @Override
    public List<Long> getUserIds(Long roleId) {
        getByIdOrThrow(roleId);
        List<SysUserRole> relations = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream().map(SysUserRole::getUserId).collect(Collectors.toList());
    }

    @Override
    public PageResult<UserVO> unassignedUsersPage(Long roleId, RoleUnassignedUserPageQueryDTO dto) {
        getByIdOrThrow(roleId);
        // 该角色已分配的用户ID
        List<Long> assignedUserIds = userRoleMapper.selectList(
                        Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).collect(Collectors.toList());

        Page<SysUser> userPage = dto.toPage(SysUser.class);
        IPage<SysUser> result = userMapper.selectPage(userPage,
                Wrappers.<SysUser>lambdaQuery()
                        .notIn(!assignedUserIds.isEmpty(), SysUser::getId, assignedUserIds)
                        .and(StringUtils.hasText(dto.getKeyword()), w -> w
                                .like(SysUser::getUsername, dto.getKeyword())
                                .or().like(SysUser::getNickname, dto.getKeyword())
                                .or().like(SysUser::getPhone, dto.getKeyword()))
                        .orderByDesc(SysUser::getId));

        List<UserVO> records = result.getRecords().stream()
                .map(user -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(user, vo);
                    return vo;
                })
                .collect(Collectors.toList());
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(Long roleId, List<Long> userIds) {
        getByIdOrThrow(roleId);
        // 去重并校验用户全部存在
        List<Long> distinctUserIds = userIds.stream().distinct().collect(Collectors.toList());
        checkUserIdsExist(distinctUserIds);
        // 整体替换：先清旧，再插新
        userRoleMapper.delete(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
        if (!distinctUserIds.isEmpty()) {
            for (Long userId : distinctUserIds) {
                SysUserRole relation = new SysUserRole();
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                userRoleMapper.insert(relation);
            }
        }
    }

    /**
     * 根据 ID 查询角色（不存在抛出业务异常）
     *
     * @param id 角色ID
     * @return 角色实体
     */
    private SysRole getByIdOrThrow(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new ServiceException("角色不存在");
        }
        return role;
    }

    /**
     * 校验角色名称不重复
     *
     * @param name      角色名称
     * @param excludeId 需要排除的角色ID（更新时排除自身），可为 null
     */
    private void checkNameDuplicate(String name, Long excludeId) {
        Long count = roleMapper.selectCount(
                Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getName, name)
                        .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("角色名称已存在");
        }
    }

    /**
     * 校验角色标识不重复
     *
     * @param code      角色标识
     * @param excludeId 需要排除的角色ID（更新时排除自身），可为 null
     */
    private void checkCodeDuplicate(String code, Long excludeId) {
        Long count = roleMapper.selectCount(
                Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getCode, code)
                        .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("角色标识已存在");
        }
    }

    /**
     * 校验权限ID全部存在
     *
     * @param permissionIds 权限ID集合
     */
    private void checkPermissionIdsExist(List<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        Long count = permissionMapper.selectCount(
                Wrappers.<SysPermission>lambdaQuery().in(SysPermission::getId, permissionIds));
        if (count != permissionIds.size()) {
            throw new ServiceException("存在无效的权限ID");
        }
    }

    /**
     * 校验用户ID全部存在（逻辑删除的用户自动被过滤）
     *
     * @param userIds 用户ID集合
     */
    private void checkUserIdsExist(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, userIds));
        if (count != userIds.size()) {
            throw new ServiceException("存在无效的用户ID");
        }
    }

    /**
     * 实体转 VO
     *
     * @param role 角色实体
     * @return 角色响应
     */
    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }
}
