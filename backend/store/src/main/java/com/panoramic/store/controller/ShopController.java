package com.panoramic.store.controller;

import com.panoramic.common.store.dto.ShopAuditDTO;
import com.panoramic.common.store.dto.ShopPageQueryDTO;
import com.panoramic.common.store.dto.ShopSaveDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.store.service.StoreShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺内部领域接口（store 域下沉纯域）。
 * <p>仅供端 BFF（store-bff 店主端 / admin 店铺管理）经内部 Feign（{@code /internal/store/...}）调用，
 * 不再向页面暴露公网路由；方法直接返回业务原类型（不包 RespData），错误经
 * {@code StoreDomainExceptionHandler} 以真实 HTTP 状态码传播。权限判定已收敛在端 BFF，
 * 本接口只负责执行；写操作审计 user_id 由 {@code StoreUserIdentityFilter} 从 X-User-Id 直取填充，
 * service 层再按 X-User-Type 做 owner(store)/platform(admin) 分流兜底（D5）。</p>
 */
@RestController
@RequestMapping("/internal/store/shops")
@RequiredArgsConstructor
public class ShopController {

    private final StoreShopService storeShopService;

    /**
     * 店主「我的店铺」：store-bff 调用，必带 store_id（=店主账号 id）；未开店返回 null
     */
    @GetMapping("/mine")
    public ShopVO mine(@RequestParam("storeId") Long storeId) {
        return storeShopService.mine(storeId);
    }

    /**
     * 店主保存草稿（无店则建 id=store_id 的店；已驳回回草稿并清审核留痕）
     */
    @PostMapping("/{storeId}/save")
    public void save(@PathVariable("storeId") Long storeId, @RequestBody ShopSaveDTO dto) {
        storeShopService.saveDraft(storeId, dto);
    }

    /**
     * 店主提交审核（完整资质校验后进入待审核）
     */
    @PostMapping("/{storeId}/submit")
    public void submit(@PathVariable("storeId") Long storeId, @RequestBody ShopSaveDTO dto) {
        storeShopService.submit(storeId, dto);
    }

    /**
     * 平台店铺分页列表（admin 店铺管理）
     */
    @GetMapping("/page")
    public PageResult<ShopVO> page(@Validated ShopPageQueryDTO dto) {
        return storeShopService.adminPage(dto);
    }

    /**
     * 平台店铺详情
     */
    @GetMapping("/{id}")
    public ShopVO detail(@PathVariable("id") Long id) {
        return storeShopService.adminDetail(id);
    }

    /**
     * 平台审核店铺（仅对「待审核」做条件更新，防重复/并发审核）
     */
    @PostMapping("/{id}/audit")
    public void audit(@PathVariable("id") Long id, @RequestBody ShopAuditDTO dto) {
        storeShopService.adminAudit(id, dto);
    }
}
