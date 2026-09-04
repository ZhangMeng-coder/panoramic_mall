package com.panoramic.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.admin.dto.PermissionSaveDTO;
import com.panoramic.admin.dto.PermissionUpdateDTO;
import com.panoramic.admin.entity.SysPermission;
import com.panoramic.admin.mapper.SysPermissionMapper;
import com.panoramic.admin.service.PermissionService;
import com.panoramic.admin.service.RolePermissionService;
import com.panoramic.admin.vo.PermissionTreeVO;
import com.panoramic.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 * <p>类型层级规则：目录(1) → 页面(2) → 按钮(3)，每层 +1。
 * 根(parentId=0)只许目录；子类型必须等于父类型+1；按钮(3)不可再有子。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl extends ServiceImpl<SysPermissionMapper, SysPermission> implements PermissionService {

    /** 类型：目录 */
    private static final int TYPE_DIR = 1;
    /** 类型：页面 */
    private static final int TYPE_MENU = 2;
    /** 类型：按钮 */
    private static final int TYPE_BUTTON = 3;

    /** 跨实体：角色-权限关联服务（菜单按角色过滤、删除权限引用守卫） */
    private final RolePermissionService rolePermissionService;

    @Override
    public List<PermissionTreeVO> tree() {
        // 平铺查询（排序后），再按 parentId 分组递归组装成树
        List<SysPermission> permissions = selectAll();
        if (permissions.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<SysPermission>> byParent = permissions.stream()
                .collect(Collectors.groupingBy(SysPermission::getParentId));
        return buildChildren(byParent, 0L);
    }

    @Override
    public List<PermissionTreeVO> menus() {
        // 临时目录接口：全量返回（目录+页面），供前端动态生成侧边菜单。
        // TODO 鉴权接入后：改为传入当前登录用户拥有的角色ID集合 —— menusByRoleIds(userRoleIds)
        return menusByRoleIds(null);
    }

    @Override
    public List<PermissionTreeVO> menusByRoleIds(List<Long> roleIds) {
        List<SysPermission> menus;
        if (roleIds == null || roleIds.isEmpty()) {
            // 全量：目录+页面
            menus = selectDirAndPages();
        } else {
            // 预留：仅返回这些角色拥有的 目录+页面。
            // TODO 鉴权接入后复核：为保证菜单树完整，还需向上补全所返回页面的祖先目录。
            List<Long> ownedIds = rolePermissionService.permissionIdsByRoleIds(roleIds);
            if (ownedIds.isEmpty()) {
                return new ArrayList<>();
            }
            menus = list(
                    Wrappers.<SysPermission>lambdaQuery()
                            .in(SysPermission::getType, TYPE_DIR, TYPE_MENU)
                            .in(SysPermission::getId, ownedIds)
                            .orderByAsc(SysPermission::getSort)
                            .orderByAsc(SysPermission::getId));
        }
        if (menus.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<SysPermission>> byParent = menus.stream()
                .collect(Collectors.groupingBy(SysPermission::getParentId));
        return buildChildren(byParent, 0L);
    }

    /**
     * 查询全部 目录+页面 节点（排序后）
     *
     * @return 目录/页面列表
     */
    private List<SysPermission> selectDirAndPages() {
        return list(
                Wrappers.<SysPermission>lambdaQuery()
                        .in(SysPermission::getType, TYPE_DIR, TYPE_MENU)
                        .orderByAsc(SysPermission::getSort)
                        .orderByAsc(SysPermission::getId));
    }

    @Override
    public PermissionTreeVO detail(Long id) {
        return toVO(getByIdOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePermission(PermissionSaveDTO dto) {
        Long parentId = dto.getParentId();
        Integer type = dto.getType();

        if (parentId == 0L) {
            // 根节点只允许目录
            if (type != TYPE_DIR) {
                throw new ServiceException("顶级节点只能是目录");
            }
        } else {
            SysPermission parent = getByIdOrThrow(parentId);
            // 子类型必须等于父类型+1（目录→页面→按钮，逐层递减）
            if (type != parent.getType() + 1) {
                throw new ServiceException("权限层级不合法：子类型必须是父类型下一级（目录→页面→按钮）");
            }
        }
        // 页面级权限供前端菜单导航，必须携带路由地址
        if (type == TYPE_MENU && !StringUtils.hasText(dto.getRoute())) {
            throw new ServiceException("页面权限需要填写路由地址");
        }
        checkNameDuplicate(dto.getName(), parentId, null);

        SysPermission permission = new SysPermission();
        BeanUtils.copyProperties(dto, permission);
        if (permission.getSort() == null) {
            permission.setSort(0);
        }
        save(permission);
        return permission.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePermission(Long id, PermissionUpdateDTO dto) {
        SysPermission permission = getByIdOrThrow(id);
        // 保持原有父级与类型，仅更新名称/权限字符串/图标/路由地址/排序
        // 页面级权限供前端菜单导航，必须携带路由地址
        if (permission.getType() == TYPE_MENU && !StringUtils.hasText(dto.getRoute())) {
            throw new ServiceException("页面权限需要填写路由地址");
        }
        checkNameDuplicate(dto.getName(), permission.getParentId(), id);

        permission.setName(dto.getName());
        permission.setPerms(dto.getPerms());
        permission.setIcon(dto.getIcon());
        permission.setRoute(dto.getRoute());
        permission.setSort(dto.getSort() == null ? 0 : dto.getSort());
        updateById(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long id) {
        getByIdOrThrow(id);
        // 存在直接子权限即拦截（树形结构下必然拦截所有后代）
        long childCount = count(
                Wrappers.<SysPermission>lambdaQuery().eq(SysPermission::getParentId, id));
        if (childCount > 0) {
            throw new ServiceException("存在子权限，无法删除");
        }
        // 已被角色引用时拒绝删除
        if (rolePermissionService.countByPermissionId(id) > 0) {
            throw new ServiceException("该权限已分配给角色，无法删除");
        }
        removeById(id);
    }

    @Override
    public boolean existsAll(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        List<Long> distinctIds = ids.stream().distinct().collect(Collectors.toList());
        return count(Wrappers.<SysPermission>lambdaQuery().in(SysPermission::getId, distinctIds)) == distinctIds.size();
    }

    /**
     * 查询全量权限（排序后）
     *
     * @return 权限列表
     */
    private List<SysPermission> selectAll() {
        return list(
                Wrappers.<SysPermission>lambdaQuery()
                        .orderByAsc(SysPermission::getSort)
                        .orderByAsc(SysPermission::getId));
    }

    /**
     * 根据 ID 查询权限（不存在抛出业务异常）
     *
     * @param id 权限ID
     * @return 权限实体
     */
    private SysPermission getByIdOrThrow(Long id) {
        SysPermission permission = getById(id);
        if (permission == null) {
            throw new ServiceException("权限不存在");
        }
        return permission;
    }

    /**
     * 递归构建指定父权限下的子权限树
     *
     * @param byParent 按父权限ID分组的全量权限
     * @param parentId 父权限ID（0 表示顶级）
     * @return 子权限树列表
     */
    private List<PermissionTreeVO> buildChildren(Map<Long, List<SysPermission>> byParent, Long parentId) {
        List<SysPermission> nodes = byParent.getOrDefault(parentId, Collections.emptyList());
        return nodes.stream().map(permission -> {
            PermissionTreeVO vo = toVO(permission);
            vo.setChildren(buildChildren(byParent, permission.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 校验同一父节点下权限名称不重复
     *
     * @param name      权限名称
     * @param parentId  父权限ID
     * @param excludeId 需要排除的权限ID（更新时排除自身），可为 null
     */
    private void checkNameDuplicate(String name, Long parentId, Long excludeId) {
        long count = count(
                Wrappers.<SysPermission>lambdaQuery()
                        .eq(SysPermission::getParentId, parentId)
                        .eq(SysPermission::getName, name)
                        .ne(excludeId != null, SysPermission::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("同级下已存在同名权限");
        }
    }

    /**
     * 实体转 VO
     *
     * @param permission 权限实体
     * @return 权限节点
     */
    private PermissionTreeVO toVO(SysPermission permission) {
        PermissionTreeVO vo = new PermissionTreeVO();
        BeanUtils.copyProperties(permission, vo);
        return vo;
    }
}
