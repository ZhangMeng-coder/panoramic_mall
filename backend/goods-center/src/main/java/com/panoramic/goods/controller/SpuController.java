package com.panoramic.goods.controller;

import com.panoramic.common.goods.dto.SpuPageQueryDTO;
import com.panoramic.common.goods.dto.SpuSaveDTO;
import com.panoramic.common.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.common.goods.dto.SpuStatusDTO;
import com.panoramic.common.goods.dto.SpuUpdateDTO;
import com.panoramic.common.goods.vo.PageResult;
import com.panoramic.common.goods.vo.SpuDetailVO;
import com.panoramic.common.goods.vo.SpuPageItemVO;
import com.panoramic.common.valid.ValidationGroups;
import com.panoramic.goods.service.SpuService;
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

/**
 * 标准商品平台 · 标准商品 SPU（模板）内部领域接口（goods-center 下沉纯域）。
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/goods/...}）调用，不再向页面暴露公网路由；
 * 方法直接返回业务结果类型（不包 RespData），错误经 {@code GoodsDomainExceptionHandler}
 * 以真实 HTTP 状态码传播。权限判定已收敛在端 BFF（@PreAuthorize），本接口只负责执行；
 * 写操作的审计 user_id 由 {@code GoodsUserIdentityFilter} 从 BFF 透传的 X-User-Id 信任头直取填充。</p>
 */
@RestController
@RequestMapping("/internal/goods/spu")
@RequiredArgsConstructor
public class SpuController {

    private final SpuService spuService;

    /**
     * 标准模板分页检索（分类/品牌/状态/名称关键字筛选）
     */
    @GetMapping("/page")
    public PageResult<SpuPageItemVO> page(@Validated SpuPageQueryDTO dto) {
        return spuService.page(dto);
    }

    /**
     * 标准模板详情/快照（含 SKU 列表、规格属性配置、分类完整链条）
     */
    @GetMapping("/{id}")
    public SpuDetailVO detail(@PathVariable Long id) {
        return spuService.detail(id);
    }

    /**
     * 新建模板（基础信息 + 规格属性配置；SKU 由 /{id}/skus 单独维护）
     */
    @PostMapping
    public Long save(@Validated(ValidationGroups.Create.class) @RequestBody SpuSaveDTO dto) {
        return spuService.saveSpu(dto);
    }

    /**
     * 更新模板（仅基础信息 + 规格属性配置）
     */
    @PutMapping("/{id}")
    public void update(@PathVariable Long id,
                       @Validated(ValidationGroups.Update.class) @RequestBody SpuUpdateDTO dto) {
        spuService.updateSpu(id, dto);
    }

    /**
     * 全量替换模板 SKU（规格管理专用；空 skus = 清空全部 SKU）
     */
    @PutMapping("/{id}/skus")
    public void replaceSkus(@PathVariable Long id,
                            @RequestBody SpuSkuReplaceDTO dto) {
        spuService.replaceSkus(id, dto);
    }

    /**
     * 模板展示/隐藏切换
     */
    @PutMapping("/{id}/status")
    public void updateStatus(@PathVariable Long id,
                             @Validated @RequestBody SpuStatusDTO dto) {
        spuService.updateStatus(id, dto);
    }

    /**
     * 删除模板（展示中拒绝；级联逻辑删除 SKU）
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        spuService.deleteSpu(id);
    }
}
