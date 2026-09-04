package com.panoramic.admin.controller;

import com.panoramic.admin.dto.RoleUnassignedUserPageQueryDTO;
import com.panoramic.admin.dto.UserPageQueryDTO;
import com.panoramic.admin.dto.UserRoleIdsDTO;
import com.panoramic.admin.dto.UserSaveDTO;
import com.panoramic.admin.dto.UserUpdateDTO;
import com.panoramic.admin.service.UserService;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.UserVO;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户分页查询
     */
    @GetMapping("/page")
    public RespData<PageResult<UserVO>> page(@Validated UserPageQueryDTO dto) {
        return RespData.success(userService.page(dto));
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    public RespData<UserVO> detail(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        return RespData.success(userService.detail(id));
    }

    /**
     * 新建用户
     */
    @PostMapping
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody UserSaveDTO dto) {
        return RespData.success(userService.saveUser(dto));
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public RespData<Void> update(@PathVariable @NotNull(message = "用户ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody UserUpdateDTO dto) {
        userService.updateUser(id, dto);
        return RespData.success();
    }

    /**
     * 删除用户（同时清理其角色分配）
     */
    @DeleteMapping("/{id}")
    public RespData<Void> delete(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        userService.deleteUser(id);
        return RespData.success();
    }

    /**
     * 查询用户已分配的角色ID集合
     */
    @GetMapping("/{id}/roles")
    public RespData<List<Long>> roleIds(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        return RespData.success(userService.getUserRoleIds(id));
    }

    /**
     * 给用户分配角色（整体替换）
     */
    @PutMapping("/{id}/roles")
    public RespData<Void> assignRoles(@PathVariable @NotNull(message = "用户ID不能为空") Long id,
                                      @RequestBody @Validated UserRoleIdsDTO dto) {
        userService.assignRoles(id, dto.getRoleIds());
        return RespData.success();
    }

    /**
     * 分页查询“不在指定角色内”的用户（角色下分配用户页面用；候选用户归属用户侧）
     */
    @GetMapping("/unassigned/page")
    public RespData<PageResult<UserVO>> unassignedUsersPage(
            @RequestParam @NotNull(message = "角色ID不能为空") Long roleId,
            @Validated RoleUnassignedUserPageQueryDTO dto) {
        return RespData.success(userService.unassignedUsersPage(roleId, dto));
    }
}
