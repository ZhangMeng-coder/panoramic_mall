package com.panoramic.goods.controller;

import com.panoramic.contract.goods.dto.BrandPageQueryDTO;
import com.panoramic.contract.goods.dto.BrandSaveDTO;
import com.panoramic.contract.goods.dto.BrandUpdateDTO;
import com.panoramic.contract.goods.vo.BrandVO;
import com.panoramic.contract.goods.vo.PageResult;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import com.panoramic.goods.service.BrandService;
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
 * 标准商品平台 · 品牌内部领域接口（goods-center 下沉纯域）。
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/goods/...}）调用，不再向页面暴露公网路由。
 * 出参一律包 {@code RespData<T>}（cross-cutting 第 2 条）：业务结果（含业务失败）走 HTTP 200 + {@code code}，
 * 异常由 common 的 {@code GlobalExceptionHandler} 兜底成 HTTP 500 —— 那是熔断唯一的失败信号（第 13 条）。
 * 权限判定已收敛在端 BFF（@PreAuthorize），本接口只负责执行；
 * 写操作的审计 user_id 由 {@code GoodsUserIdentityFilter} 从 BFF 透传的 X-User-Id 信任头直取填充。</p>
 */
@RestController
@RequestMapping("/internal/goods/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /**
     * 品牌分页查询
     */
    @GetMapping("/page")
    public RespData<PageResult<BrandVO>> page(@Validated BrandPageQueryDTO dto) {
        return RespData.success(brandService.page(dto));
    }

    /**
     * 全量品牌列表（商品表单下拉选择用）
     */
    @GetMapping("/list")
    public RespData<List<BrandVO>> list() {
        return RespData.success(brandService.listAll());
    }

    /**
     * 品牌详情
     */
    @GetMapping("/{id}")
    public RespData<BrandVO> detail(@PathVariable Long id) {
        return RespData.success(brandService.detail(id));
    }

    /**
     * 新建品牌
     */
    @PostMapping
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody BrandSaveDTO dto) {
        return RespData.success(brandService.saveBrand(dto));
    }

    /**
     * 更新品牌
     */
    @PutMapping("/{id}")
    public RespData<Void> update(@PathVariable Long id,
                                @Validated(ValidationGroups.Update.class) @RequestBody BrandUpdateDTO dto) {
        brandService.updateBrand(id, dto);
        return RespData.success();
    }

    /**
     * 删除品牌（被商品引用时拒绝）
     */
    @DeleteMapping("/{id}")
    public RespData<Void> delete(@PathVariable Long id) {
        brandService.deleteBrand(id);
        return RespData.success();
    }
}
