package com.panoramic.common.goods.api;

import com.panoramic.common.goods.dto.BrandPageQueryDTO;
import com.panoramic.common.goods.dto.BrandSaveDTO;
import com.panoramic.common.goods.dto.BrandUpdateDTO;
import com.panoramic.common.goods.dto.CategorySaveDTO;
import com.panoramic.common.goods.dto.CategoryUpdateDTO;
import com.panoramic.common.goods.dto.SpuPageQueryDTO;
import com.panoramic.common.goods.dto.SpuSaveDTO;
import com.panoramic.common.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.common.goods.dto.SpuStatusDTO;
import com.panoramic.common.goods.dto.SpuUpdateDTO;
import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.goods.vo.PageResult;
import com.panoramic.common.goods.vo.SpuBySkuCodeVO;
import com.panoramic.common.goods.vo.SpuDetailVO;
import com.panoramic.common.goods.vo.SpuPageItemVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * goods-center（标准商品平台 / 标准商品模板库）内部 Feign 客户端。
 * <p>下沉纯域 goods-center 不再向页面暴露公网路由，由各端 BFF 经本接口内部调用。
 * 规约（见 CLAUDE.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 common（goods-center 服务端、本客户端引用同一份类型）；</li>
 *   <li>方法直接返回业务结果类型（不包 RespData），错误走异常统一传播；</li>
 *   <li>调用经 {@link GoodsFeignConfiguration} 附带信任头 + 熔断 + 错误解码。</li>
 * </ul>
 * 服务端路径与映射需与 goods-center 内部控制器一一对应（前缀 /internal/goods）。</p>
 */
@FeignClient(name = "goods-center", contextId = "goodsCenterClient",
        path = "/internal/goods", configuration = GoodsFeignConfiguration.class)
public interface GoodsCenterClient {

    // ---- 品牌 ----
    @GetMapping("/brands/page")
    PageResult<BrandVO> pageBrands(@SpringQueryMap BrandPageQueryDTO dto);

    @GetMapping("/brands/list")
    List<BrandVO> listBrands();

    @GetMapping("/brands/{id}")
    BrandVO brandDetail(@PathVariable("id") Long id);

    @PostMapping("/brands")
    Long saveBrand(@RequestBody BrandSaveDTO dto);

    @PutMapping("/brands/{id}")
    void updateBrand(@PathVariable("id") Long id, @RequestBody BrandUpdateDTO dto);

    @DeleteMapping("/brands/{id}")
    void deleteBrand(@PathVariable("id") Long id);

    // ---- 分类 ----
    @PostMapping("/categories")
    Long saveCategory(@RequestBody CategorySaveDTO dto);

    @GetMapping("/categories/tree")
    List<CategoryTreeVO> categoryTree();

    @PutMapping("/categories/{id}")
    void updateCategory(@PathVariable("id") Long id, @RequestBody CategoryUpdateDTO dto);

    @DeleteMapping("/categories/{id}")
    void deleteCategory(@PathVariable("id") Long id);

    // ---- 标准商品 SPU（模板） ----
    @GetMapping("/spu/page")
    PageResult<SpuPageItemVO> pageSpu(@SpringQueryMap SpuPageQueryDTO dto);

    @GetMapping("/spu/{id}")
    SpuDetailVO spuDetail(@PathVariable("id") Long id);

    /**
     * 按 SKU 编码反查所属标准商品（店铺端「填 SKU_CODE 预填新增表单」用）。
     * <p>中台 goods_sku.sku_code 无唯一索引，重复时按 SKU id 升序取首条并置 matchedSkuCount；
     * <b>未命中返回 spu=null</b>（HTTP 200，不抛异常）——店铺端允许「查不到照样自建」。</p>
     */
    @GetMapping("/spu/by-sku-code")
    SpuBySkuCodeVO spuDetailBySkuCode(@RequestParam("skuCode") String skuCode);

    @PostMapping("/spu")
    Long saveSpu(@RequestBody SpuSaveDTO dto);

    @PutMapping("/spu/{id}")
    void updateSpu(@PathVariable("id") Long id, @RequestBody SpuUpdateDTO dto);

    @PutMapping("/spu/{id}/skus")
    void replaceSpuSkus(@PathVariable("id") Long id, @RequestBody SpuSkuReplaceDTO dto);

    @PutMapping("/spu/{id}/status")
    void updateSpuStatus(@PathVariable("id") Long id, @RequestBody SpuStatusDTO dto);

    @DeleteMapping("/spu/{id}")
    void deleteSpu(@PathVariable("id") Long id);
}
