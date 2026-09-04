package com.panoramic.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.admin.entity.SysRolePermission;
import com.panoramic.admin.mapper.SysRolePermissionMapper;
import com.panoramic.admin.service.RolePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色-权限关联服务实现（纯关联表，物理删除）
 */
@Slf4j
@Service
public class RolePermissionServiceImpl extends ServiceImpl<SysRolePermissionMapper, SysRolePermission>
        implements RolePermissionService {

    @Override
    public List<Long> permissionIdsByRoleId(Long roleId) {
        List<SysRolePermission> relations = list(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream().map(SysRolePermission::getPermissionId).collect(Collectors.toList());
    }

    @Override
    public List<Long> permissionIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(Wrappers.<SysRolePermission>lambdaQuery().in(SysRolePermission::getRoleId, roleIds))
                .stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public long countByPermissionId(Long permissionId) {
        return count(Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getPermissionId, permissionId));
    }

    @Override
    public void removeByRoleId(Long roleId) {
        remove(Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId));
    }

    @Override
    public void replaceByRole(Long roleId, Collection<Long> permissionIds) {
        removeByRoleId(roleId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        for (Long permissionId : permissionIds.stream().distinct().collect(Collectors.toList())) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            save(relation);
        }
    }
}
