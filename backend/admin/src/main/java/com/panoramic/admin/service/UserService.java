package com.panoramic.admin.service;

import com.panoramic.admin.dto.UserPageQueryDTO;
import com.panoramic.admin.dto.UserSaveDTO;
import com.panoramic.admin.dto.UserUpdateDTO;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.UserVO;

import java.util.List;

/**
 * 用户服务
 */
public interface UserService {

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
}
