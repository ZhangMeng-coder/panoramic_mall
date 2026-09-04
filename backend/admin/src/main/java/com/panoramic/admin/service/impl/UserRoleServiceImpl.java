package com.panoramic.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.admin.entity.SysUserRole;
import com.panoramic.admin.mapper.SysUserRoleMapper;
import com.panoramic.admin.service.UserRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户-角色关联服务实现（纯关联表，物理删除）
 */
@Slf4j
@Service
public class UserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements UserRoleService {

    @Override
    public List<SysUserRole> listByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, userIds));
    }

    @Override
    public List<Long> roleIdsByUserId(Long userId) {
        List<SysUserRole> relations = list(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    public List<Long> userIdsByRoleId(Long roleId) {
        List<SysUserRole> relations = list(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream().map(SysUserRole::getUserId).collect(Collectors.toList());
    }

    @Override
    public long countByRoleId(Long roleId) {
        return count(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
    }

    @Override
    public void removeByUserId(Long userId) {
        remove(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
    }

    @Override
    public void removeByRoleId(Long roleId) {
        remove(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, roleId));
    }

    @Override
    public void replaceByUser(Long userId, Collection<Long> roleIds) {
        removeByUserId(userId);
        insertRelations(null, userId, roleIds);
    }

    @Override
    public void replaceByRole(Long roleId, Collection<Long> userIds) {
        removeByRoleId(roleId);
        insertRelations(roleId, null, userIds);
    }

    /**
     * 逐条插入关联记录（入参已整体替换，先清旧后插新）
     *
     * @param roleId  角色ID（按角色替换时非空）
     * @param userId  用户ID（按用户替换时非空）
     * @param otherIds 目标集合（可为空/null）
     */
    private void insertRelations(Long roleId, Long userId, Collection<Long> otherIds) {
        if (otherIds == null || otherIds.isEmpty()) {
            return;
        }
        for (Long otherId : otherIds.stream().distinct().collect(Collectors.toList())) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(roleId == null ? userId : otherId);
            relation.setRoleId(roleId == null ? otherId : roleId);
            save(relation);
        }
    }
}
