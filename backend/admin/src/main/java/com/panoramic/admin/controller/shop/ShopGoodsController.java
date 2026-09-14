package com.panoramic.admin.controller.shop;

import com.panoramic.admin.bff.ShopGoodsBffService;
import com.panoramic.admin.dto.ShopGoodsPageQueryDTO;
import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.store.dto.StoreGoodsLockDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopOptionVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPlatformPageItemVO;
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

import java.util.List;

/**
 * admin 端 BFF · 店铺商品管理编排接口（面向 admin 前端，2026-09-12 新增）。
 * <p>页面请求经网关 {@code /admin/shop/goods/**} → 本控制器 → 内部 Feign 调 store 域
 * platform 侧（跨店全量）。平台可查看<b>任意店铺</b>的商品、按 类型（分类，含子树）/
 * 品牌 / 店铺 筛选，并锁定 / 解锁商品。只做聚合与包装（RespData），不持有 store 域实体与表。</p>
 * <p>权限：查询类（列表/详情/三个下拉）走 {@code store:goods:list}，锁定与解锁走
 * {@code store:goods:lock}——三个下拉由本控制器代理（A4），复用查询权限，不再单独开权限串。
 * 域内不做任何权限判断，{@code @PreAuthorize} 是唯一授权点。</p>
 * <p>锁定语义：锁定时域侧把名下已上架 SKU 级联下架、SPU 随之推导为下架；解锁清空锁定字段但
 * <b>不恢复上架</b>（由店主手动重新上架）。</p>
 */
@RestController
@RequestMapping("/shop/goods")
@RequiredArgsConstructor
public class ShopGoodsController {

    private final ShopGoodsBffService shopGoodsBffService;

    /**
     * 店铺商品分页列表（跨店全量；keyword/categoryId（含子树）/brandId/storeId/shelfStatus/lockStatus）
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('store:goods:list')")
    public RespData<PageResult<StoreGoodsSpuPlatformPageItemVO>> page(@Validated ShopGoodsPageQueryDTO dto) {
        return RespData.success(shopGoodsBffService.pageGoods(dto));
    }

    /**
     * 商品详情（跨店只读，含 SKU 列表与锁定信息）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('store:goods:list')")
    public RespData<StoreGoodsSpuPlatformDetailVO> detail(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        return RespData.success(shopGoodsBffService.detailGoods(id));
    }

    /**
     * 锁定商品（原因必填）：锁定时名下已上架 SKU 级联下架，SPU 推导为下架；锁定期店主侧整行只读
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('store:goods:lock')")
    public RespData<Void> lock(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                               @Valid @RequestBody StoreGoodsLockDTO dto) {
        shopGoodsBffService.lockGoods(id, dto);
        return RespData.success();
    }

    /**
     * 解锁商品：清空锁定字段；<b>不恢复上架</b>（SKU 保持下架，由店主手动重新上架）
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('store:goods:lock')")
    public RespData<Void> unlock(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        shopGoodsBffService.unlockGoods(id);
        return RespData.success();
    }

    /**
     * 分类树（分类筛选下拉，允许选任意层级；子树展开在 BFF 内用同一棵树完成）
     */
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('store:goods:list')")
    public RespData<List<CategoryTreeVO>> categoryTree() {
        return RespData.success(shopGoodsBffService.categoryTree());
    }

    /**
     * 品牌列表（品牌筛选下拉）
     */
    @GetMapping("/brands")
    @PreAuthorize("hasAuthority('store:goods:list')")
    public RespData<List<BrandVO>> brands() {
        return RespData.success(shopGoodsBffService.listBrands());
    }

    /**
     * 店铺下拉选项（按店铺筛选；来自 store 域 platform 侧，不按审核状态过滤）
     */
    @GetMapping("/shops")
    @PreAuthorize("hasAuthority('store:goods:list')")
    public RespData<List<ShopOptionVO>> shopOptions() {
        return RespData.success(shopGoodsBffService.listShopOptions());
    }
}
