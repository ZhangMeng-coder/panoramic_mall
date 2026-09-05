package com.panoramic.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.admin.dto.RoleUnassignedUserPageQueryDTO;
import com.panoramic.admin.dto.UserPageQueryDTO;
import com.panoramic.admin.dto.UserSaveDTO;
import com.panoramic.admin.dto.UserUpdateDTO;
import com.panoramic.admin.entity.SysUser;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.UserVO;

import java.util.Collection;
import java.util.List;

/**
 * 用户服务
 */
public interface UserService extends IService<SysUser> {

    /**
     * 用户分页查询
     *
     * @param dto 分页查询参数（关键字模糊匹配用户名/昵称/手机号，可选状态过滤）
     * @return 分页结果
     */
    PageResult<UserVO> page(UserPageQueryDTO dto);

    /**
     * 用户详情
     *
     * @param id 用户ID
     * @return 用户响应（不含密码）
     */
    UserVO detail(Long id);

    /**
     * 新建用户
     *
     * @param dto 新建请求
     * @return 新用户ID
     */
    Long saveUser(UserSaveDTO dto);

    /**
     * 更新用户
     *
     * @param id  用户ID
     * @param dto 更新请求
     */
    void updateUser(Long id, UserUpdateDTO dto);

    /**
     * 删除用户（同时清理该用户的角色分配记录）
     *
     * @param id 用户ID
     */
    void deleteUser(Long id);

    /**
     * 查询用户已分配的角色ID集合
     *
     * @param userId 用户ID
     * @return 角色ID列表
     */
    List<Long> getUserRoleIds(Long userId);

    /**
     * 给用户分配角色（整体替换）
     *
     * @param userId  用户ID
     * @param roleIds 目标角色ID集合，可为空（清空）
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 分页查询“不在该角色内”的用户（角色下分配用户页面用；接口归属用户侧）
     *
     * @param roleId 角色ID
     * @param dto    分页查询参数（关键字）
     * @return 分页结果
     */
    PageResult<UserVO> unassignedUsersPage(Long roleId, RoleUnassignedUserPageQueryDTO dto);

    /**
     * 校验用户 ID 是否全部存在（去重后比较数量；供角色侧分配用户校验，逻辑删除的用户自动被过滤）
     *
     * @param ids 用户ID集合，可为空/null
     * @return true=全部存在（或集合为空）
     */
    boolean existsAll(Collection<Long> ids);

    /**
     * 按用户名取登录用用户（显式把密码列带出，供登录校验；查询/返回均不暴露密码）
     *
     * @param username 用户名
     * @return 用户实体（含 password/status/nickname/avatar），不存在返回 null
     */
    SysUser getForAuthByUsername(String username);

    /**
     * 修改密码（当前登录用户本人）：校验原密码正确后更新为新密码的 BCrypt 摘要
     *
     * @param userId      用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码（明文，由本方法加密存储）
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
}
