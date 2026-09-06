package com.panoramic.admin.bff;

import com.panoramic.common.exception.ServiceException;
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
     * 统一编排执行：业务异常（400 参数/业务、403 权限）透传，其余（熔断/连接/序列化等）降级为友好提示。
     * <p>⚠ Feign + 熔断会把下游抛出的业务异常包装成 {@code NoFallbackAvailableException}/
     * {@code ExecutionException}/{@code CompletionException} 等再抛出，因此须沿 cause 链定位原始
     * {@link ServiceException}；否则 400/403 会被误当成连接故障降级为 500「服务暂不可用」。</p>
     */
    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (Exception e) {
            // 沿 cause 链找下游业务异常，剥开熔断/异步包装层
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof ServiceException se) {
                    Integer code = se.getCode();
                    if (code != null && (code == 400 || code == 403)) {
                        throw se; // 参数/业务(400)、权限(403)：原样透传，由统一异常处理还原给页面
                    }
                    log.warn("goods-center 调用异常，降级处理: code={}, msg={}", se.getCode(), se.getMessage());
                    throw new ServiceException(500, DEGRADE_MSG);
                }
            }
            // 非业务异常：熔断开启 / 连接失败 / 序列化等 → 降级为友好提示
            log.error("goods-center 调用失败，降级处理", e);
            throw new ServiceException(500, DEGRADE_MSG);
        }
    }
}
