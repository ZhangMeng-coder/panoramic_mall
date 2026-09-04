package com.panoramic.admin.controller;

import com.panoramic.admin.dto.RolePageQueryDTO;
import com.panoramic.admin.dto.RolePermissionIdsDTO;
import com.panoramic.admin.dto.RoleSaveDTO;
import com.panoramic.admin.dto.RoleUpdateDTO;
import com.panoramic.admin.dto.RoleUserIdsDTO;
import com.panoramic.admin.service.RoleService;
import com.panoramic.admin.vo.PageResult;
import com.panoramic.admin.vo.RoleVO;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色接口
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * 角色分页查询
     */
    @GetMapping("/page")
    public RespData<PageResult<RoleVO>> page(@Validated RolePageQueryDTO dto) {
        return RespData.success(roleService.page(dto));
    }

    /**
     * 全量角色列表（下拉选择用）
     */
    @GetMapping("/list")
    public RespData<List<RoleVO>> list() {
        return RespData.success(roleService.listAll());
    }

    /**
     * 角色详情
     */
    @GetMapping("/{id}")
    public RespData<RoleVO> detail(@PathVariable @NotNull(message = "角色ID不能为空") Long id) {
        return RespData.success(roleService.detail(id));
    }

    /**
     * 新建角色
     */
    @PostMapping
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody RoleSaveDTO dto) {
        return RespData.success(roleService.saveRole(dto));
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    public RespData<Void> update(@PathVariable @NotNull(message = "角色ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody RoleUpdateDTO dto) {
        roleService.updateRole(id, dto);
        return RespData.success();
    }

    /**
     * 删除角色（已分配给用户时拒绝）
     */
    @DeleteMapping("/{id}")
    public RespData<Void> delete(@PathVariable @NotNull(message = "角色ID不能为空") Long id) {
        roleService.deleteRole(id);
        return RespData.success();
    }

    /**
     * 查询角色已分配的权限ID集合
     */
    @GetMapping("/{id}/permissions")
    public RespData<List<Long>> permissionIds(@PathVariable @NotNull(message = "角色ID不能为空") Long id) {
        return RespData.success(roleService.getRolePermissionIds(id));
    }

    /**
     * 给角色分配权限（整体替换）
     */
    @PutMapping("/{id}/permissions")
    public RespData<Void> assignPermissions(@PathVariable @NotNull(message = "角色ID不能为空") Long id,
                                            @RequestBody @Validated RolePermissionIdsDTO dto) {
        roleService.assignPermissions(id, dto.getPermissionIds());
        return RespData.success();
    }

    /**
     * 查询角色下已分配的用户ID集合
     */
    @GetMapping("/{id}/user-ids")
    public RespData<List<Long>> userRoleIds(@PathVariable @NotNull(message = "角色ID不能为空") Long id) {
        return RespData.success(roleService.getUserIds(id));
    }

    /**
     * 给角色分配用户（整体替换）
     */
    @PutMapping("/{id}/users")
    public RespData<Void> assignUsers(@PathVariable @NotNull(message = "角色ID不能为空") Long id,
                                      @RequestBody @Validated RoleUserIdsDTO dto) {
        roleService.assignUsers(id, dto.getUserIds());
        return RespData.success();
    }
}
