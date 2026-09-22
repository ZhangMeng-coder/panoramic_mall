package com.panoramic.store.controller;

import com.panoramic.contract.store.dto.ShopAuditDTO;
import com.panoramic.contract.store.dto.ShopPageQueryDTO;
import com.panoramic.contract.store.dto.ShopSaveDTO;
import com.panoramic.contract.store.dto.StoreScopeGroup;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopOptionVO;
import com.panoramic.contract.store.vo.ShopVO;
import com.panoramic.store.service.StoreShopService;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 店铺内部领域接口（store 域下沉纯域）。
 * <p>仅供端 BFF（store-bff 店主端 / admin 店铺管理）经内部 Feign（{@code /internal/store/...}）调用，
 * 不再向页面暴露公网路由；方法直接返回业务原类型（不包 RespData），错误经
 * {@code StoreDomainExceptionHandler} 以真实 HTTP 状态码传播。权限判定已收敛在端 BFF，
 * 本接口只负责执行；写操作审计 user_id 由 {@code StoreUserIdentityFilter} 从 X-User-Id 直取填充。</p>
 * <p><b>接口按能力通用、不按端分侧</b>（cross-cutting 第 22 条）：每条能力只有一条路径，
 * 数据作用域由<b>入参</b>携带——店主侧传自己的 {@code storeId}（= 账号 id）、管理端跨店不传，
 * 域内只做「传了就按它筛，没传就是不限定」，不判身份、不读 {@code X-User-Type} 判权。</p>
 */
@RestController
@RequestMapping("/internal/store/shops")
@RequiredArgsConstructor
public class ShopController {

    private final StoreShopService storeShopService;

    /**
     * 保存草稿（{@code dto.storeId} 必填：无店则建 id=storeId 的店；已驳回回草稿并清审核留痕）
     */
    @PostMapping("/save")
    public void save(@Validated({Default.class, StoreScopeGroup.class}) @RequestBody ShopSaveDTO dto) {
        storeShopService.saveDraft(dto.getStoreId(), dto);
    }

    /**
     * 提交审核（{@code dto.storeId} 必填：完整资质校验后进入待审核）
     */
    @PostMapping("/submit")
    public void submit(@Validated({Default.class, StoreScopeGroup.class}) @RequestBody ShopSaveDTO dto) {
        storeShopService.submit(dto.getStoreId(), dto);
    }

    /**
     * 店铺分页列表（admin 店铺管理；全量，无作用域维度）
     */
    @GetMapping("/page")
    public PageResult<ShopVO> page(@Validated ShopPageQueryDTO dto) {
        return storeShopService.adminPage(dto);
    }

    /**
     * 店铺详情（一条路径服务所有调用方）。
     * <p>⚠ <b>查不到一律返空</b>（HTTP 200 空 body → Feign 解出 {@code null}），域内不抛：
     * 店主侧「未开店」、C 端「店铺不可见」都是正常态。调用方各自重判——store-bff 判「未开店」、
     * admin 转本层「店铺不存在」、mall-bff 判「不可见」（见 docs/contracts/store.md 第三节）。</p>
     */
    @GetMapping("/{id}")
    public ShopVO detail(@PathVariable("id") Long id) {
        return storeShopService.getShop(id);
    }

    /**
     * 审核店铺（仅对「待审核」做条件更新，防重复/并发审核）
     */
    @PostMapping("/{id}/audit")
    public void audit(@PathVariable("id") Long id, @RequestBody ShopAuditDTO dto) {
        storeShopService.adminAudit(id, dto);
    }

    /**
     * 平台店铺下拉选项（管理后台「店铺商品管理」按店铺筛选用）。
     * <p>不按审核状态过滤：未审核通过的店铺本就没有商品，过滤无收益。</p>
     */
    @GetMapping("/options")
    public List<ShopOptionVO> options() {
        return storeShopService.options();
    }
}
