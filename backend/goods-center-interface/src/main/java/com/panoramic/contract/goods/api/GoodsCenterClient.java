package com.panoramic.contract.goods.api;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.goods.dto.BrandPageQueryDTO;
import com.panoramic.contract.goods.dto.BrandSaveDTO;
import com.panoramic.contract.goods.dto.BrandUpdateDTO;
import com.panoramic.contract.goods.dto.CategorySaveDTO;
import com.panoramic.contract.goods.dto.CategoryUpdateDTO;
import com.panoramic.contract.goods.dto.SpuPageQueryDTO;
import com.panoramic.contract.goods.dto.SpuSaveDTO;
import com.panoramic.contract.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.contract.goods.dto.SpuStatusDTO;
import com.panoramic.contract.goods.dto.SpuUpdateDTO;
import com.panoramic.contract.goods.vo.BrandVO;
import com.panoramic.contract.goods.vo.CategoryTreeVO;
import com.panoramic.contract.goods.vo.PageResult;
import com.panoramic.contract.goods.vo.SpuBySkuCodeVO;
import com.panoramic.contract.goods.vo.SpuDetailVO;
import com.panoramic.contract.goods.vo.SpuPageItemVO;
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
import java.util.Map;

/**
 * goods-center（标准商品平台 / 标准商品模板库）内部 Feign 客户端。
 * <p>下沉纯域 goods-center 不再向页面暴露公网路由，由各端 BFF 经本接口内部调用。
 * 规约（见 docs/contracts/goods-center.md 与 cross-cutting.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 goods-center-interface（goods-center 服务端、本客户端引用同一份类型）；</li>
 *   <li>出参一律包 {@code RespData<T>}，<b>HTTP 200 承载一切业务结果</b>：正常 {@code code=200} + {@code data}，
 *       业务失败 {@code code=400/403/404} + {@code msg}（域侧不抛异常，故 {@code ErrorDecoder} 不会被调用）；
 *       只有真故障才是 HTTP 5xx → 调用方抛 {@code FeignException} → 计入熔断。调用方解包走
 *       {@code com.panoramic.common.feign.DomainResp#unwrap}（端 BFF 再经 {@code BffFeignCall#call} 叠降级）；</li>
 *   <li>调用经 {@link GoodsFeignConfiguration} 附带信任头；熔断由<b>调用方</b>经 Nacos
 *       {@code feign-circuitbreaker.yml} 配置提供，不在本类。</li>
 * </ul>
 * 服务端路径与映射需与 goods-center 内部控制器一一对应（前缀 /internal/goods）。</p>
 */
@FeignClient(name = "goods-center", contextId = "goodsCenterClient",
        path = "/internal/goods", configuration = GoodsFeignConfiguration.class)
public interface GoodsCenterClient {

    // ---- 品牌 ----
    @GetMapping("/brands/page")
    RespData<PageResult<BrandVO>> pageBrands(@SpringQueryMap BrandPageQueryDTO dto);

    @GetMapping("/brands/list")
    RespData<List<BrandVO>> listBrands();

    @GetMapping("/brands/{id}")
    RespData<BrandVO> brandDetail(@PathVariable("id") Long id);

    @PostMapping("/brands")
    RespData<Long> saveBrand(@RequestBody BrandSaveDTO dto);

    @PutMapping("/brands/{id}")
    RespData<Void> updateBrand(@PathVariable("id") Long id, @RequestBody BrandUpdateDTO dto);

    @DeleteMapping("/brands/{id}")
    RespData<Void> deleteBrand(@PathVariable("id") Long id);

    // ---- 分类 ----
    @PostMapping("/categories")
    RespData<Long> saveCategory(@RequestBody CategorySaveDTO dto);

    @GetMapping("/categories/tree")
    RespData<List<CategoryTreeVO>> categoryTree();

    /**
     * 批量取分类全路径（如「服饰 / 男装 / T恤」，以 " / " 连接）。
     * <p>端 BFF 读时解析用：域（store）不持分类表，故由端 BFF 拿商品行上的 categoryId 集合批量换路径。
     * 结果只含命中的 id（查不到的不出现在 Map 里），调用方对缺失项回退显示落库快照名。
     * 入参为空集合时直接返回空 Map。</p>
     */
    @PostMapping("/categories/paths")
    RespData<Map<Long, String>> categoryPaths(@RequestBody List<Long> categoryIds);

    @PutMapping("/categories/{id}")
    RespData<Void> updateCategory(@PathVariable("id") Long id, @RequestBody CategoryUpdateDTO dto);

    @DeleteMapping("/categories/{id}")
    RespData<Void> deleteCategory(@PathVariable("id") Long id);

    // ---- 标准商品 SPU（模板） ----
    @GetMapping("/spu/page")
    RespData<PageResult<SpuPageItemVO>> pageSpu(@SpringQueryMap SpuPageQueryDTO dto);

    @GetMapping("/spu/{id}")
    RespData<SpuDetailVO> spuDetail(@PathVariable("id") Long id);

    /**
     * 按 SKU 编码反查所属标准商品（店铺端「填 SKU_CODE 预填新增表单」用）。
     * <p>中台 goods_sku.sku_code 无唯一索引，重复时按 SKU id 升序取首条并置 matchedSkuCount；
     * <b>未命中返回 spu=null</b>（{@code code=200} + {@code data.spu=null}，不报业务错）——
     * 店铺端允许「查不到照样自建」。</p>
     */
    @GetMapping("/spu/by-sku-code")
    RespData<SpuBySkuCodeVO> spuDetailBySkuCode(@RequestParam("skuCode") String skuCode);

    @PostMapping("/spu")
    RespData<Long> saveSpu(@RequestBody SpuSaveDTO dto);

    @PutMapping("/spu/{id}")
    RespData<Void> updateSpu(@PathVariable("id") Long id, @RequestBody SpuUpdateDTO dto);

    @PutMapping("/spu/{id}/skus")
    RespData<Void> replaceSpuSkus(@PathVariable("id") Long id, @RequestBody SpuSkuReplaceDTO dto);

    @PutMapping("/spu/{id}/status")
    RespData<Void> updateSpuStatus(@PathVariable("id") Long id, @RequestBody SpuStatusDTO dto);

    @DeleteMapping("/spu/{id}")
    RespData<Void> deleteSpu(@PathVariable("id") Long id);
}
