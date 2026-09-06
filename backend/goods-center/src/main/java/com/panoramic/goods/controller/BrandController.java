package com.panoramic.goods.controller;

import com.panoramic.common.goods.dto.BrandPageQueryDTO;
import com.panoramic.common.goods.dto.BrandSaveDTO;
import com.panoramic.common.goods.dto.BrandUpdateDTO;
import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.PageResult;
import com.panoramic.common.valid.ValidationGroups;
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
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/goods/...}）调用，不再向页面暴露公网路由；
 * 方法直接返回业务结果类型（不包 RespData），错误经 {@code GoodsDomainExceptionHandler}
 * 以真实 HTTP 状态码传播。权限判定已收敛在端 BFF（@PreAuthorize），本接口只负责执行；
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
    public PageResult<BrandVO> page(@Validated BrandPageQueryDTO dto) {
        return brandService.page(dto);
    }

    /**
     * 全量品牌列表（商品表单下拉选择用）
     */
    @GetMapping("/list")
    public List<BrandVO> list() {
        return brandService.listAll();
    }

    /**
     * 品牌详情
     */
    @GetMapping("/{id}")
    public BrandVO detail(@PathVariable Long id) {
        return brandService.detail(id);
    }

    /**
     * 新建品牌
     */
    @PostMapping
    public Long save(@Validated(ValidationGroups.Create.class) @RequestBody BrandSaveDTO dto) {
        return brandService.saveBrand(dto);
    }

    /**
     * 更新品牌
     */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id,
                       @Validated(ValidationGroups.Update.class) @RequestBody BrandUpdateDTO dto) {
        brandService.updateBrand(id, dto);
    }

    /**
     * 删除品牌（被商品引用时拒绝）
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        brandService.deleteBrand(id);
    }
}
