package com.panoramic.admin.bff;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.goods.api.GoodsCenterClient;
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
import com.panoramic.common.goods.vo.SpuDetailVO;
import com.panoramic.common.goods.vo.SpuPageItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * admin 端 BFF · 标准商品模板维护编排。
 * <p>只做页面编排与聚合，不持有/复制 goods 域任何实体与表；全部经内部 Feign 调 goods-center
 * （共享 DTO 同源在 common），并按 Feign 规约熔断：
 * 下游业务异常（400 参数/业务、403 权限）原样透传由统一异常处理还原 RespData 给页面；
 * 连接失败 / 熔断开启 / 其它异常统一降级为友好提示，避免拖垮调用方。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsTemplateBffService {

    /** 下游熔断/连接异常降级提示 */
    private static final String DEGRADE_MSG = "商品服务暂不可用，请稍后重试";

    private final GoodsCenterClient goodsCenterClient;

    // ---- 品牌 ----
    public PageResult<BrandVO> pageBrands(BrandPageQueryDTO dto) {
        return call(() -> goodsCenterClient.pageBrands(dto));
    }

    public List<BrandVO> listBrands() {
        return call(goodsCenterClient::listBrands);
    }

    public BrandVO brandDetail(Long id) {
        return call(() -> goodsCenterClient.brandDetail(id));
    }

    public Long saveBrand(BrandSaveDTO dto) {
        return call(() -> goodsCenterClient.saveBrand(dto));
    }

    public void updateBrand(Long id, BrandUpdateDTO dto) {
        call(() -> {
            goodsCenterClient.updateBrand(id, dto);
            return null;
        });
    }

    public void deleteBrand(Long id) {
        call(() -> {
            goodsCenterClient.deleteBrand(id);
            return null;
        });
    }

    // ---- 分类 ----
    public Long saveCategory(CategorySaveDTO dto) {
        return call(() -> goodsCenterClient.saveCategory(dto));
    }

    public List<CategoryTreeVO> categoryTree() {
        return call(goodsCenterClient::categoryTree);
    }

    public void updateCategory(Long id, CategoryUpdateDTO dto) {
        call(() -> {
            goodsCenterClient.updateCategory(id, dto);
            return null;
        });
    }

    public void deleteCategory(Long id) {
        call(() -> {
            goodsCenterClient.deleteCategory(id);
            return null;
        });
    }

    // ---- 标准商品 SPU（模板） ----
    public PageResult<SpuPageItemVO> pageSpu(SpuPageQueryDTO dto) {
        return call(() -> goodsCenterClient.pageSpu(dto));
    }

    public SpuDetailVO spuDetail(Long id) {
        return call(() -> goodsCenterClient.spuDetail(id));
    }

    public Long saveSpu(SpuSaveDTO dto) {
        return call(() -> goodsCenterClient.saveSpu(dto));
    }

    public void updateSpu(Long id, SpuUpdateDTO dto) {
        call(() -> {
            goodsCenterClient.updateSpu(id, dto);
            return null;
        });
    }

    public void replaceSpuSkus(Long id, SpuSkuReplaceDTO dto) {
        call(() -> {
            goodsCenterClient.replaceSpuSkus(id, dto);
            return null;
        });
    }

    public void updateSpuStatus(Long id, SpuStatusDTO dto) {
        call(() -> {
            goodsCenterClient.updateSpuStatus(id, dto);
            return null;
        });
    }

    public void deleteSpu(Long id) {
        call(() -> {
            goodsCenterClient.deleteSpu(id);
            return null;
        });
    }

    /**
     * 统一编排执行：业务异常（400 参数/业务、403、404）透传，其余（熔断/连接/序列化等）降级为友好提示。
     * <p>剥 cause 链与降级的实现已抽到 common 的 {@link BffFeignCall}（各端 BFF 共用一份），
     * 本类只传自己的降级文案。</p>
     */
    private <T> T call(Supplier<T> action) {
        return BffFeignCall.call("goods-center", DEGRADE_MSG, action);
    }
}
