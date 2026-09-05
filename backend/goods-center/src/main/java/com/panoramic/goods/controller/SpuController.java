package com.panoramic.goods.controller;

import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.common.vo.RespData;
import com.panoramic.goods.dto.SpuPageQueryDTO;
import com.panoramic.goods.dto.SpuSaveDTO;
import com.panoramic.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.goods.dto.SpuStatusDTO;
import com.panoramic.goods.dto.SpuUpdateDTO;
import com.panoramic.goods.service.SpuService;
import com.panoramic.goods.vo.PageResult;
import com.panoramic.goods.vo.SpuDetailVO;
import com.panoramic.goods.vo.SpuPageItemVO;
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

/**
 * 商品（SPU）接口
 */
@RestController
@RequestMapping("/spu")
@RequiredArgsConstructor
public class SpuController {

    private final SpuService spuService;

    /**
     * 商品分页查询
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('goods:spu:list')")
    public RespData<PageResult<SpuPageItemVO>> page(@Validated SpuPageQueryDTO dto) {
        return RespData.success(spuService.page(dto));
    }

    /**
     * 商品详情（含 SKU 列表、规格属性配置、分类完整链条）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:spu:list')")
    public RespData<SpuDetailVO> detail(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        return RespData.success(spuService.detail(id));
    }

    /**
     * 新建商品（基础信息 + 规格属性配置；SKU 由 /{id}/skus 单独维护）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('goods:spu:add')")
    public RespData<Long> save(@Validated(ValidationGroups.Create.class) @RequestBody SpuSaveDTO dto) {
        return RespData.success(spuService.saveSpu(dto));
    }

    /**
     * 更新商品（仅基础信息 + 规格属性配置）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:spu:edit')")
    public RespData<Void> update(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                 @Validated(ValidationGroups.Update.class) @RequestBody SpuUpdateDTO dto) {
        spuService.updateSpu(id, dto);
        return RespData.success();
    }

    /**
     * 全量替换商品 SKU（规格管理专用；空 skus = 清空全部 SKU）
     */
    @PutMapping("/{id}/skus")
    @PreAuthorize("hasAuthority('goods:spu:edit')")
    public RespData<Void> replaceSkus(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                      @RequestBody SpuSkuReplaceDTO dto) {
        spuService.replaceSkus(id, dto);
        return RespData.success();
    }

    /**
     * 商品展示/隐藏切换
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('goods:spu:edit')")
    public RespData<Void> updateStatus(@PathVariable @NotNull(message = "商品ID不能为空") Long id,
                                       @Validated @RequestBody SpuStatusDTO dto) {
        spuService.updateStatus(id, dto);
        return RespData.success();
    }

    /**
     * 删除商品（展示中拒绝）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('goods:spu:delete')")
    public RespData<Void> delete(@PathVariable @NotNull(message = "商品ID不能为空") Long id) {
        spuService.deleteSpu(id);
        return RespData.success();
    }
}
