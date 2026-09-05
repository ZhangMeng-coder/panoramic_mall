package com.panoramic.admin.controller;

import com.panoramic.admin.dto.PermissionSaveDTO;
import com.panoramic.admin.dto.PermissionUpdateDTO;
import com.panoramic.admin.service.PermissionService;
import com.panoramic.admin.vo.PermissionTreeVO;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 权限接口
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 全量权限树（含按钮，权限管理页用；需系统权限查询）
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:permission:list')")
    public RespData<List<PermissionTreeVO>> tree() {
        return RespData.success(permissionService.tree());
    }

    /**
     * 前端目录接口（仅目录+页面）——按当前登录用户角色过滤返回其可见菜单
     */
    @GetMapping("/menus")
    public RespData<List<PermissionTreeVO>> menus() {
        return RespData.success(permissionService.menusByRoleIds(UserContext.getRoleIds()));
    }

    /**
     * 权限详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:list')")
    public RespData<PermissionTreeVO> detail(@PathVariable @NotNull(message = "权限ID不能为空") Long id) {
        return RespData.success(permissionService.detail(id));
    }

    /**
     * 新建权限
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:permission:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody PermissionSaveDTO dto) {
        return RespData.success(permissionService.savePermission(dto));
    }

    /**
     * 更新权限（仅名称/权限字符串/图标/排序）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "权限ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody PermissionUpdateDTO dto) {
        permissionService.updatePermission(id, dto);
        return RespData.success();
    }

    /**
     * 删除权限（存在子权限或已被角色引用时拒绝）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "权限ID不能为空") Long id) {
        permissionService.deletePermission(id);
        return RespData.success();
    }
}
