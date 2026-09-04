package com.panoramic.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.admin.dto.RolePageQueryDTO;
import com.panoramic.admin.dto.RoleSaveDTO;
import com.panoramic.admin.dto.RoleUpdateDTO;
import com.panoramic.admin.entity.SysRole;
import com.panoramic.admin.mapper.SysRoleMapper;
import com.panoramic.admin.service.PermissionService;
import com.panoramic.admin.service.RolePermissionService;
import com.panoramic.admin.service.RoleService;
import com.panoramic.admin.service.UserRoleService;
import com.panoramic.admin.service.UserService;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.RoleVO;
import com.panoramic.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements RoleService {

    /** 跨实体：用户服务（分配用户时用户ID存在性校验；与 UserService→RoleService 形成校验层循环引用，此处经 @Lazy 断环） */
    @Lazy
    private final UserService userService;
    /** 跨实体：权限服务（权限ID存在性校验） */
    private final PermissionService permissionService;
    /** 跨实体：用户-角色关联服务（分配/清理/查询） */
    private final UserRoleService userRoleService;
    /** 跨实体：角色-权限关联服务（权限分配/清理/查询） */
    private final RolePermissionService rolePermissionService;

    @Override
    public PageResult<RoleVO> page(RolePageQueryDTO dto) {
        Page<SysRole> rolePage = dto.toPage(SysRole.class);
        IPage<SysRole> result = page(rolePage,
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
        return list(
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
        save(role);
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
        updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        getByIdOrThrow(id);
        // 已分配给用户 → 拒绝
        if (userRoleService.countByRoleId(id) > 0) {
            throw new ServiceException("该角色已分配给用户，无法删除");
        }
        // 存在权限分配关系 → 连带清理关系后再删除角色
        rolePermissionService.removeByRoleId(id);
        removeById(id);
    }

    @Override
    public List<Long> getRolePermissionIds(Long roleId) {
        getByIdOrThrow(roleId);
        return rolePermissionService.permissionIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        getByIdOrThrow(roleId);
        // 去重并校验权限全部存在
        List<Long> distinctPermissionIds = permissionIds.stream().distinct().collect(Collectors.toList());
        if (!permissionService.existsAll(distinctPermissionIds)) {
            throw new ServiceException("存在无效的权限ID");
        }
        // 整体替换：先清旧，再插新
        rolePermissionService.replaceByRole(roleId, distinctPermissionIds);
    }

    @Override
    public List<Long> getUserIds(Long roleId) {
        getByIdOrThrow(roleId);
        return userRoleService.userIdsByRoleId(roleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(Long roleId, List<Long> userIds) {
        getByIdOrThrow(roleId);
        // 去重并校验用户全部存在（逻辑删除的用户自动被过滤）
        List<Long> distinctUserIds = userIds.stream().distinct().collect(Collectors.toList());
        if (!userService.existsAll(distinctUserIds)) {
            throw new ServiceException("存在无效的用户ID");
        }
        // 整体替换：先清旧，再插新
        userRoleService.replaceByRole(roleId, distinctUserIds);
    }

    @Override
    public List<RoleVO> listVOsByIdsSorted(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return list(Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getId, ids)
                        .orderByAsc(SysRole::getSort)
                        .orderByAsc(SysRole::getId))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public boolean existsAll(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> distinctIds = ids.stream().distinct().collect(Collectors.toList());
        return count(Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, distinctIds)) == distinctIds.size();
    }

    /**
     * 根据 ID 查询角色（不存在抛出业务异常）
     *
     * @param id 角色ID
     * @return 角色实体
     */
    private SysRole getByIdOrThrow(Long id) {
        SysRole role = getById(id);
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
        Long count = count(
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
        Long count = count(
                Wrappers.<SysRole>lambdaQuery()
                        .eq(SysRole::getCode, code)
                        .ne(excludeId != null, SysRole::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("角色标识已存在");
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
