package com.panoramic.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.panoramic.admin.dto.UserPageQueryDTO;
import com.panoramic.admin.dto.UserSaveDTO;
import com.panoramic.admin.dto.UserUpdateDTO;
import com.panoramic.admin.entity.SysRole;
import com.panoramic.admin.entity.SysUser;
import com.panoramic.admin.entity.SysUserRole;
import com.panoramic.admin.mapper.SysRoleMapper;
import com.panoramic.admin.mapper.SysUserMapper;
import com.panoramic.admin.mapper.SysUserRoleMapper;
import com.panoramic.admin.service.UserService;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.RoleVO;
import com.panoramic.admin.vo.UserVO;
import com.panoramic.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PageResult<UserVO> page(UserPageQueryDTO dto) {
        Page<SysUser> userPage = dto.toPage(SysUser.class);
        IPage<SysUser> result = userMapper.selectPage(userPage,
                Wrappers.<SysUser>lambdaQuery()
                        .and(StringUtils.hasText(dto.getKeyword()), w -> w
                                .like(SysUser::getUsername, dto.getKeyword())
                                .or().like(SysUser::getNickname, dto.getKeyword())
                                .or().like(SysUser::getPhone, dto.getKeyword()))
                        .eq(dto.getStatus() != null, SysUser::getStatus, dto.getStatus())
                        .orderByDesc(SysUser::getId));

        List<UserVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        // 批量回填本页用户的角色（多角色并排展示），避免逐行查询
        attachRoles(records);
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public UserVO detail(Long id) {
        return toVO(getByIdOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveUser(UserSaveDTO dto) {
        checkUsernameDuplicate(dto.getUsername(), null);
        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserUpdateDTO dto) {
        SysUser user = getByIdOrThrow(id);
        checkUsernameDuplicate(dto.getUsername(), id);
        BeanUtils.copyProperties(dto, user);
        // 密码为空表示不修改（实体中 password 默认不随 select 查出，故不会被误写）
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else {
            user.setPassword(null);
        }
        userMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        getByIdOrThrow(id);
        // 物理删除该用户的角色分配记录，再逻辑删除用户本身
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        getByIdOrThrow(userId);
        List<SysUserRole> relations = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        return relations.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        getByIdOrThrow(userId);
        // 去重并校验角色全部存在
        List<Long> distinctRoleIds = roleIds.stream().distinct().collect(Collectors.toList());
        checkRoleIdsExist(distinctRoleIds);
        // 整体替换：先清旧，再插新
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        if (!distinctRoleIds.isEmpty()) {
            for (Long roleId : distinctRoleIds) {
                SysUserRole relation = new SysUserRole();
                relation.setUserId(userId);
                relation.setRoleId(roleId);
                userRoleMapper.insert(relation);
            }
        }
    }

    /**
     * 根据 ID 查询用户（不存在抛出业务异常）
     *
     * @param id 用户ID
     * @return 用户实体
     */
    private SysUser getByIdOrThrow(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        return user;
    }

    /**
     * 校验用户名不重复
     *
     * @param username  用户名
     * @param excludeId 需要排除的用户ID（更新时排除自身），可为 null
     */
    private void checkUsernameDuplicate(String username, Long excludeId) {
        Long count = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, username)
                        .ne(excludeId != null, SysUser::getId, excludeId));
        if (count > 0) {
            throw new ServiceException("用户名已存在");
        }
    }

    /**
     * 校验角色ID全部存在
     *
     * @param roleIds 角色ID集合
     */
    private void checkRoleIdsExist(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        Long count = roleMapper.selectCount(
                Wrappers.<SysRole>lambdaQuery().in(SysRole::getId, roleIds));
        if (count != roleIds.size()) {
            throw new ServiceException("存在无效的角色ID");
        }
    }

    /**
     * 批量回填用户已分配的角色（供列表并排展示）。
     * <p>两次批量查询（关联表 + 角色表），按角色 sort/id 排序后逐用户组装，无角色置空列表。</p>
     *
     * @param records 本页用户 VO 列表
     */
    private void attachRoles(List<UserVO> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> userIds = records.stream().map(UserVO::getId).collect(Collectors.toList());
        List<SysUserRole> relations = userRoleMapper.selectList(
                Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            records.forEach(vo -> vo.setRoles(Collections.emptyList()));
            return;
        }
        List<Long> roleIds = relations.stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        // 角色按 sort/id 排序，保证每个用户内多角色展示顺序稳定
        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getId, roleIds)
                        .orderByAsc(SysRole::getSort)
                        .orderByAsc(SysRole::getId));
        // 外层遍历已按 sort/id 排序的 roles，保证每个用户内多角色顺序稳定
        Map<Long, List<RoleVO>> rolesByUser = new HashMap<>();
        for (SysRole role : roles) {
            RoleVO vo = new RoleVO();
            BeanUtils.copyProperties(role, vo);
            for (SysUserRole relation : relations) {
                if (role.getId().equals(relation.getRoleId())) {
                    rolesByUser.computeIfAbsent(relation.getUserId(), k -> new ArrayList<>()).add(vo);
                }
            }
        }
        records.forEach(vo ->
                vo.setRoles(rolesByUser.getOrDefault(vo.getId(), Collections.emptyList())));
    }

    /**
     * 实体转 VO（密码字段 select=false，不参与查询与返回）
     *
     * @param user 用户实体
     * @return 用户响应
     */
    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
