package com.panoramic.store.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.store.dto.ShopAuditDTO;
import com.panoramic.store.dto.ShopPageQueryDTO;
import com.panoramic.store.service.StoreShopService;
import com.panoramic.store.vo.PageResult;
import com.panoramic.store.vo.ShopVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台店铺管理接口（归属 admin，经网关 /store/** 前缀转发，受 RBAC 权限控制）
 */
@RestController
@RequestMapping("/admin/shops")
@RequiredArgsConstructor
public class AdminShopController {

    private final StoreShopService storeShopService;

    /**
     * 店铺分页列表（按状态/关键字筛选）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('store:shop:list')")
    public RespData<PageResult<ShopVO>> page(@Validated ShopPageQueryDTO dto) {
        return RespData.success(storeShopService.adminPage(dto));
    }

    /**
     * 店铺详情（含店主账号）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:shop:list')")
    public RespData<ShopVO> detail(@PathVariable @NotNull(message = "店铺ID不能为空") Long id) {
        return RespData.success(storeShopService.adminDetail(id));
    }

    /**
     * 店铺审核：通过/驳回（驳回原因必填）；仅对「待审核」生效，防重复审核
     */
    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('store:shop:audit')")
    public RespData<Void> audit(@PathVariable @NotNull(message = "店铺ID不能为空") Long id,
                                @Valid @RequestBody ShopAuditDTO dto) {
        storeShopService.adminAudit(id, dto);
        return RespData.success();
    }
}
