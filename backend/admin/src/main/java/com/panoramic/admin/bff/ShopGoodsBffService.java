package com.panoramic.admin.bff;

import com.panoramic.admin.dto.ShopGoodsPageQueryDTO;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.goods.api.GoodsCenterClient;
import com.panoramic.common.goods.vo.BrandVO;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.store.api.StoreClient;
import com.panoramic.common.store.dto.StoreGoodsLockDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopOptionVO;
import com.panoramic.common.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.common.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * admin 端 BFF · 店铺商品管理编排（2026-09-12 新增）。
 * <p>只做页面编排与聚合，不持有/复制 store 域与 goods-center 的任何实体与表：
 * 商品数据经 {@link StoreClient} 调 store 域 <b>platform 侧</b>（不带 store_id、跨店全量，
 * 权限由本端 {@code @PreAuthorize store:goods:*} 把关）；分类树 / 品牌列表经
 * {@link GoodsCenterClient} 调 goods-center；店铺下拉经 {@link StoreClient} 调 store 域。</p>
 * <p><b>分类子树匹配（A1）</b>：前端级联选择器只回一个 {@code categoryId}，但用户期望
 * 「选中父分类 = 含其全部子分类的商品」。域不持分类表、无法自行展开，故由本层取分类树
 * 递归收集「该节点 + 全部后代」组成 {@code categoryIds} 传给域（域侧只做 {@code IN} 过滤）。
 * 分类树取不到或树中无该节点时<b>退化为按选中节点自身过滤</b>——筛选变窄可感知，
 * 但绝不因中台抖动让整个列表失败。</p>
 * <p><b>分类全路径（D4/D9）</b>：列表按页内去重后的分类 id <b>批量</b>调 goods-center 换路径
 * （一次调用，非 N+1）；解析走独立 try/catch 降级，失败仅告警、路径留空，
 * 前端回退落库快照 {@code categoryName}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopGoodsBffService {

    /** store 域熔断/连接异常降级提示 */
    private static final String STORE_DEGRADE_MSG = "店铺服务暂不可用，请稍后重试";
    /** goods-center 熔断/连接异常降级提示 */
    private static final String GOODS_DEGRADE_MSG = "商品服务暂不可用，请稍后重试";

    private final StoreClient storeClient;
    private final GoodsCenterClient goodsCenterClient;

    // ---- 店铺商品（数据在 store 域跨店通用的分页接口，本端不设限定条件走全量）----

    /**
     * 店铺商品分页（跨店全量）：分类单选入参展开为子树多值 → 域分页 → 分类全路径读时回填
     *
     * @param dto 页面查询参数
     * @return 分页结果（含 storeName / skuCount / 锁定信息 / categoryPath）
     */
    public PageResult<StoreGoodsSpuCrossShopPageItemVO> pageGoods(ShopGoodsPageQueryDTO dto) {
        StoreGoodsSpuCrossShopPageQueryDTO query = new StoreGoodsSpuCrossShopPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setKeyword(dto.getKeyword());
        query.setBrandIds(dto.getBrandIds());
        query.setStoreId(dto.getStoreId());
        query.setShelfStatus(dto.getShelfStatus());
        query.setLockStatus(dto.getLockStatus());
        query.setCategoryIds(expandCategoryIds(dto.getCategoryId()));

        PageResult<StoreGoodsSpuCrossShopPageItemVO> result =
                callStore(() -> storeClient.pageStoreGoodsCrossShop(query));
        fillCategoryPaths(result.getRecords());
        return result;
    }

    /**
     * 店铺商品详情（跨店，只读）：域详情 + 分类全路径 + <b>描述消毒</b>
     *
     * <p>⚠ {@code description} 是<b>店主</b>自由录入的富文本（域侧原样存取、不清洗），而本页
     * 把它 {@code v-html} 渲染到<b>平台管理员</b>的会话里 —— 不洗就是店主对管理员页面的存储型
     * XSS。洗在出口这一处（前端不再各自去引清洗库），用的是 common 的 {@link HtmlSanitizer}，
     * 与 mall-bff 同一份白名单（见 docs/contracts/cross-cutting.md 第 21 条）。</p>
     *
     * @param id 店铺商品 id
     * @return 详情（含 SKU 列表、锁定信息、storeName）
     */
    public StoreGoodsSpuPlatformDetailVO detailGoods(Long id) {
        StoreGoodsSpuPlatformDetailVO vo = callStore(() -> storeClient.platformStoreGoodsDetail(id));
        vo.setCategoryPath(resolveCategoryPath(vo.getCategoryId()));
        vo.setDescription(HtmlSanitizer.sanitizeRichText(vo.getDescription()));
        return vo;
    }

    /**
     * 锁定商品（原因必填）：域侧写锁定字段 + 名下已上架 SKU 级联下架 → SPU 推导为下架
     *
     * @param id  店铺商品 id
     * @param dto 锁定原因
     */
    public void lockGoods(Long id, StoreGoodsLockDTO dto) {
        callStore(() -> {
            storeClient.lockStoreGoods(id, dto);
            return null;
        });
    }

    /**
     * 解锁商品：清空锁定字段；<b>不恢复上架</b>（SKU 保持下架，由店主手动重新上架）
     *
     * @param id 店铺商品 id
     */
    public void unlockGoods(Long id) {
        callStore(() -> {
            storeClient.unlockStoreGoods(id);
            return null;
        });
    }

    // ---- 筛选/表单下拉（分类、品牌来自 goods-center，店铺来自 store 域）----

    /**
     * 分类树（分类筛选级联 + 子树展开用）
     */
    public List<CategoryTreeVO> categoryTree() {
        return callGoods(goodsCenterClient::categoryTree);
    }

    /**
     * 品牌列表（品牌筛选用，非必填）
     */
    public List<BrandVO> listBrands() {
        return callGoods(goodsCenterClient::listBrands);
    }

    /**
     * 店铺下拉选项（按店铺筛选；不做审核状态过滤——未过审的店本就没有商品）
     */
    public List<ShopOptionVO> listShopOptions() {
        return callStore(storeClient::listShopOptions);
    }

    // ---- 分类子树展开（域不持分类表，展开只能在 BFF 做）----

    /**
     * 把页面单选分类展开为「该节点 + 全部后代」的 id 列表（A1 子树匹配）。
     * <p>分类树取不到、或树中找不到该节点时，退化为只按该节点自身过滤并记 warn——
     * 筛选范围变窄是可感知的降级，但不会让列表整体失败。</p>
     *
     * @param categoryId 页面选中的分类 id（null = 不按分类过滤）
     * @return 待传给域的分类 id 列表；不筛分类时返回 null
     */
    private List<Long> expandCategoryIds(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        try {
            Set<Long> ids = collectDescendantIds(callGoods(goodsCenterClient::categoryTree), categoryId);
            if (ids != null) {
                return new ArrayList<>(ids);
            }
            log.warn("分类树中未找到分类 {}（可能已被删除），退化为按其自身过滤", categoryId);
        } catch (ServiceException e) {
            log.warn("取分类树失败，分类筛选退化为按选中节点自身过滤: categoryId={}, msg={}",
                    categoryId, e.getMessage());
        }
        return List.of(categoryId);
    }

    /**
     * 在分类树中定位 rootId，收集其<b>自身与全部后代</b> id（纯函数，不做 IO）。
     *
     * @param nodes  分类树（可为 null）
     * @param rootId 目标分类 id
     * @return 命中则返回 id 集合（至少含 rootId 自身）；树中无此节点返回 null
     */
    private Set<Long> collectDescendantIds(List<CategoryTreeVO> nodes, Long rootId) {
        if (nodes == null) {
            return null;
        }
        for (CategoryTreeVO node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            if (rootId.equals(node.getId())) {
                Set<Long> ids = new HashSet<>();
                addSelfAndDescendants(node, ids);
                return ids;
            }
            Set<Long> found = collectDescendantIds(node.getChildren(), rootId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 递归收集节点自身与全部后代 id（分类树由 goods-center 保证无环；仍不做深度限制，与域侧同款写法）
     */
    private void addSelfAndDescendants(CategoryTreeVO node, Set<Long> out) {
        out.add(node.getId());
        if (node.getChildren() == null) {
            return;
        }
        for (CategoryTreeVO child : node.getChildren()) {
            if (child != null && child.getId() != null) {
                addSelfAndDescendants(child, out);
            }
        }
    }

    // ---- 分类全路径读时解析（域不持分类表，路径只能在本层补）----

    /**
     * 批量回填列表项的分类全路径：只对本页去重后的分类 id 发一次请求，避免 N+1。
     * <p>未命中的行保持 {@code categoryPath == null}，前端回退显示落库快照 {@code categoryName}。</p>
     *
     * @param items 本页列表项（就地回填）
     */
    private void fillCategoryPaths(List<StoreGoodsSpuCrossShopPageItemVO> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> categoryIds = items.stream()
                .map(StoreGoodsSpuCrossShopPageItemVO::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> paths = fetchCategoryPaths(categoryIds);
        if (paths.isEmpty()) {
            return;
        }
        for (StoreGoodsSpuCrossShopPageItemVO item : items) {
            item.setCategoryPath(paths.get(item.getCategoryId()));
        }
    }

    /**
     * 解析单条分类全路径（详情用）；未命中返回 null（前端回退快照名）
     *
     * @param categoryId 分类 id
     * @return 如「服饰 / 男装 / T恤」；解析失败或未命中为 null
     */
    private String resolveCategoryPath(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return fetchCategoryPaths(List.of(categoryId)).get(categoryId);
    }

    /**
     * 调 goods-center 批量取分类全路径。
     * <p><b>降级隔离（D9）</b>：中台不可用 / 熔断 / 业务异常一律只告警并返回空 Map——
     * 分类路径是展示增强，缺失不能把整个列表或详情拖失败（前端回退显示快照名）。</p>
     *
     * @param categoryIds 分类 id 集合（已去重）
     * @return id -> 路径；不可得时空 Map
     */
    private Map<Long, String> fetchCategoryPaths(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<Long, String> paths = callGoods(() -> goodsCenterClient.categoryPaths(categoryIds));
            return paths == null ? Collections.emptyMap() : paths;
        } catch (ServiceException e) {
            log.warn("分类全路径解析失败，回退显示快照分类名: categoryIds={}, msg={}", categoryIds, e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ---- 编排辅助 ----

    /**
     * 调 store 域的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callStore(Supplier<T> action) {
        return BffFeignCall.call("store", STORE_DEGRADE_MSG, action);
    }

    /**
     * 调 goods-center 的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T callGoods(Supplier<T> action) {
        return BffFeignCall.call("goods-center", GOODS_DEGRADE_MSG, action);
    }
}
