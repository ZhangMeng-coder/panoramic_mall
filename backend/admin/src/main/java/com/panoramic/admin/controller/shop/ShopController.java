package com.panoramic.admin.controller.shop;

import com.panoramic.admin.bff.StoreShopBffService;
import com.panoramic.common.store.dto.ShopAuditDTO;
import com.panoramic.common.store.dto.ShopPageQueryDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.common.vo.RespData;
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
 * admin 端 BFF · 店铺管理编排接口（面向 admin 前端）。
 * <p>页面请求经网关 {@code /admin/shop/shops/**} → 本控制器 → 内部 Feign 调 store 域
 * platform 接口。只做聚合/包装（RespData），不持有 store 域实体与表；不做店主账号回填（D6）。
 * 沿用 RBAC 权限串 {@code store:shop:list}/{@code store:shop:audit}（权限种子已灌，无需新增）。</p>
 */
@RestController
@RequestMapping("/shop/shops")
@RequiredArgsConstructor
public class ShopController {

    private final StoreShopBffService storeShopBffService;

    /**
     * 店铺分页列表（按状态/关键字筛选）
     */
    @GetMapping
    @PreAuthorize("hasAuthority('store:shop:list')")
    public RespData<PageResult<ShopVO>> page(@Validated ShopPageQueryDTO dto) {
        return RespData.success(storeShopBffService.pageShops(dto));
    }

    /**
     * 店铺详情（无店主账号信息）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:shop:list')")
    public RespData<ShopVO> detail(@PathVariable @NotNull(message = "店铺ID不能为空") Long id) {
        return RespData.success(storeShopBffService.shopDetail(id));
    }

    /**
     * 店铺审核：通过/驳回（驳回原因必填）；仅对「待审核」生效，防重复审核
     */
    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('store:shop:audit')")
    public RespData<Void> audit(@PathVariable @NotNull(message = "店铺ID不能为空") Long id,
                                @Valid @RequestBody ShopAuditDTO dto) {
        storeShopBffService.auditShop(id, dto);
        return RespData.success();
    }
}
