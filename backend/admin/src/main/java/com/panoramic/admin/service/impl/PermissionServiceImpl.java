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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** 主页页面路由（type=2 页面的 route 字段）：对任意已登录用户恒可见 */
    private static final String HOME_ROUTE = "/home";

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
    public List<PermissionTreeVO> menusByRoleIds(List<Long> roleIds) {
        // 全量权限（目录/页面/按钮，有序）：目录/页面作菜单候选，全量作向上找父链依据
        List<SysPermission> allPermissions = list(
                Wrappers.<SysPermission>lambdaQuery()
                        .orderByAsc(SysPermission::getSort)
                        .orderByAsc(SysPermission::getId));
        if (allPermissions.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, SysPermission> allById = allPermissions.stream()
                .collect(Collectors.toMap(SysPermission::getId, p -> p));

        // 主页对任意已登录用户恒可见（含无角色/无权限用户），先入 keep
        Set<Long> keep = new HashSet<>();
        addHomepage(allPermissions, keep, allById);

        List<Long> ownedIds = (roleIds == null || roleIds.isEmpty())
                ? Collections.emptyList()
                : rolePermissionService.permissionIdsByRoleIds(roleIds);
        if (!ownedIds.isEmpty()) {
            // 拥有的 目录/页面 节点本身入菜单
            for (Long id : ownedIds) {
                SysPermission node = allById.get(id);
                if (node != null && node.getType() != TYPE_BUTTON) {
                    keep.add(id);
                }
            }
            // 向上补全祖先目录/页面：角色只要拥有任意节点（含只授查询按钮），
            // 其挂接的页面与目录链即完整 → 只读角色（仅持 goods:*:list）菜单可见
            for (Long id : ownedIds) {
                addAncestors(allById, keep, id);
            }
        }

        if (keep.isEmpty()) {
            return new ArrayList<>();
        }
        // 仅取 目录/页面 且命中 keep 的节点，按序组树
        List<SysPermission> menus = allPermissions.stream()
                .filter(p -> p.getType() != TYPE_BUTTON && keep.contains(p.getId()))
                .collect(Collectors.toList());
        Map<Long, List<SysPermission>> byParent = menus.stream()
                .collect(Collectors.groupingBy(SysPermission::getParentId));
        return buildChildren(byParent, 0L);
    }

    /**
     * 主页（route=HOME_ROUTE 的页面及其祖先目录）加入 keep，实现“人人可见”
     *
     * @param allPermissions 全量权限（有序）
     * @param keep           已选中的节点 id 集合（原地扩展）
     * @param allById        全量权限 id → 节点
     */
    private void addHomepage(List<SysPermission> allPermissions, Set<Long> keep, Map<Long, SysPermission> allById) {
        SysPermission homePage = allPermissions.stream()
                .filter(p -> p.getType() == TYPE_MENU && HOME_ROUTE.equals(p.getRoute()))
                .findFirst()
                .orElse(null);
        if (homePage == null) {
            return;
        }
        keep.add(homePage.getId());
        addAncestors(allById, keep, homePage.getId());
    }

    /**
     * 把 nodeId 的所有祖先（目录）补进 keep 集合，保证菜单树从根目录到页面完整
     *
     * @param byId   全量权限（含按钮）id → 节点
     * @param keep   已选中的节点 id 集合（原地扩展）
     * @param nodeId 当前节点 id
     */
    private void addAncestors(Map<Long, SysPermission> byId, Set<Long> keep, Long nodeId) {
        SysPermission node = byId.get(nodeId);
        if (node == null || node.getParentId() == null || node.getParentId() == 0L) {
            return;
        }
        Long parentId = node.getParentId();
        if (!keep.contains(parentId) && byId.containsKey(parentId)) {
            keep.add(parentId);
            addAncestors(byId, keep, parentId);
        }
    }

    @Override
    public List<String> permsOfIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return list(Wrappers.<SysPermission>lambdaQuery()
                        .in(SysPermission::getId, permissionIds)
                        .isNotNull(SysPermission::getPerms))
                .stream()
                .map(SysPermission::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
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
