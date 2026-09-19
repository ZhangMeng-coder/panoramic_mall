package com.panoramic.mallbff.service;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.goods.api.GoodsCenterClient;
import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.store.api.StoreClient;
import com.panoramic.common.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.common.store.vo.StoreGoodsFacetItemVO;
import com.panoramic.common.store.vo.StoreGoodsSkuVO;
import com.panoramic.common.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.common.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.common.util.HtmlSanitizer;
import com.panoramic.mallbff.dto.MallFacetQueryDTO;
import com.panoramic.mallbff.dto.MallGoodsPageQueryDTO;
import com.panoramic.mallbff.vo.MallFacetItemVO;
import com.panoramic.mallbff.vo.MallFacetVO;
import com.panoramic.mallbff.vo.MallGoodsDetailVO;
import com.panoramic.mallbff.vo.MallGoodsItemVO;
import com.panoramic.mallbff.vo.MallGoodsSkuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C 端商品浏览编排（分类树 / 商品分页 / 筛选聚合）。
 * <p>只做页面编排与聚合，不持有/复制任何域的实体与表：分类树与「分类子树展开」经
 * {@link GoodsCenterClient} 调 goods-center（<b>域不持分类表、无法自行展开</b>），
 * 商品分页与筛选聚合经 {@link StoreClient} 调 store 域跨店通用接口。</p>
 *
 * <p><b>C 端展示口径固定在端 BFF</b>（D-口径）：域侧「跨店通用」接口只按传入条件过滤、
 * <b>不含任何 C 端隐含约束</b>，故本层调域时固定传 {@code shopStatus=2}（已审核通过店铺）
 * + {@code shelfStatus=1}（上架）+ {@code lockStatus=0}（未被平台锁定）。
 * 漏传这三个条件会把未过审店铺与平台锁定商品漏到前台——新增 C 端查询时必须保持。</p>
 *
 * <p><b>空集合 = 不筛，不是「都不匹配」</b>（域侧 {@code IN} 只在集合非空时施加）：
 * 故分类锚点<b>解析不出子树时回退成该 id 自身</b>，绝不把空结果传下去——
 * 否则分类页会从「只出本分类」翻成「出全站」，方向与预期相反。
 * 只有「既无已选分类、又无锚点」时才返回 {@code null}（= 不按分类过滤）。</p>
 *
 * <p><b>下游异常</b>：业务 4xx（400/403/404）沿 cause 链剥出后原样透传，其余（熔断/连接/序列化等）
 * 降级为友好提示，见 {@link BffFeignCall}。分类树对 {@link #goods}/{@link #facets} 只是<b>增强</b>
 * （用于子树展开与筛选名解析），故那条路径单独吞异常降级为「无树」；而 {@link #categories()}
 * 是首页宫格的主内容，拿不到就让异常抛出（前端整块不渲染）。</p>
 *
 * <p><b>店主录入的富文本在这条出口上消毒</b>：商品详情是店主自由录入的 HTML（域侧原样存取、
 * 不清洗），故 {@link #toMallDetail} 下发前经 {@link HtmlSanitizer} 洗一遍 ——
 * 白名单只此一份、与 admin 端共用（见 docs/contracts/cross-cutting.md 第 21 条）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogBffService {

    /** store 域熔断/连接异常降级提示 */
    private static final String DOWN_MSG = "商品暂不可用，请稍后重试";
    /** goods-center 分类树降级提示（首页宫格的主内容，无退路） */
    private static final String CATEGORY_DOWN_MSG = "分类暂不可用，请稍后重试";

    /** C 端「商品不可见」的统一文案：不存在 / 已下架 / 被平台锁定 / 店铺未过审 一律不区分（不泄露存在性） */
    private static final String NOT_VISIBLE_MSG = "商品不存在或已下架";

    /** {@link BffFeignCall} 原样透传的那一类业务 4xx（400 参数/业务、403 权限、404 不存在） */
    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;

    /** C 端固定展示口径：已审核通过店铺（store_shop.status 契约：0草稿/1待审核/2已通过/3已驳回） */
    private static final Integer SHOP_STATUS_APPROVED = 2;
    /** C 端固定展示口径：上架 */
    private static final Integer SHELF_ON = 1;
    /** C 端固定展示口径：未被平台锁定 */
    private static final Integer LOCK_OFF = 0;

    private final GoodsCenterClient goodsCenterClient;
    private final StoreClient storeClient;

    /**
     * 全量分类树（首页宫格 / 分类页标题 / 分类筛选名解析共用）。
     * <p>每次实调 goods-center，不缓存（树很小）。⚠ 这里是主内容、没有退路：拿不到就让
     * {@link BffFeignCall} 把异常降级为「分类暂不可用」抛给页面，前端整块不渲染。</p>
     *
     * @return 分类树（顶级节点列表）
     */
    public List<CategoryTreeVO> categories() {
        return BffFeignCall.call("goods-center", CATEGORY_DOWN_MSG, () -> goodsCenterClient.categoryTree());
    }

    /**
     * C 端商品分页：页面选择 + C 端固定展示口径 + 分类子树展开 → store 域跨店分页 → 裁剪为 C 端形状。
     *
     * @param dto 页面查询参数
     * @return 分页结果（C 端字段，见 {@link MallGoodsItemVO}）
     */
    public PageResult<MallGoodsItemVO> goods(MallGoodsPageQueryDTO dto) {
        // 无锚点也无已选分类时树用不上（resolveCategoryIds 会直接返回 null），省掉一次跨服务调用
        boolean needTree = dto.getCategoryId() != null
                || (dto.getCategoryIds() != null && !dto.getCategoryIds().isEmpty());
        List<CategoryTreeVO> tree = needTree ? categoryTreeOrEmpty() : Collections.emptyList();
        StoreGoodsSpuCrossShopPageQueryDTO query = new StoreGoodsSpuCrossShopPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setKeyword(dto.getKeyword());
        query.setBrandIds(dto.getBrandIds());
        query.setSort(dto.getSort());
        query.setCategoryIds(resolveCategoryIds(tree, dto.getCategoryId(), dto.getCategoryIds()));
        // C 端展示口径固定在本层（域侧不含 C 端隐含约束，漏传即漏出未过审店铺/锁定商品）
        query.setShopStatus(SHOP_STATUS_APPROVED);
        query.setShelfStatus(SHELF_ON);
        query.setLockStatus(LOCK_OFF);

        PageResult<StoreGoodsSpuCrossShopPageItemVO> raw =
                BffFeignCall.call("store", DOWN_MSG, () -> storeClient.pageStoreGoodsCrossShop(query));
        PageResult<MallGoodsItemVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(this::toMallItem).collect(Collectors.toList()));
        return result;
    }

    /**
     * 筛选维度聚合（分类 / 品牌）。
     * <p>分类维度按页面分流：<b>分类页（带锚点）原样返回</b>锚点子树内的分类；<b>搜索页（无锚点）
     * 把每个分类上溯到顶级祖先并累加命中数</b>（搜索页筛选项只展示一级分类）。品牌维度恒原样映射。</p>
     * <p>⚠ 两维度互斥排除自身（域侧口径）：分类维度不受已选分类影响、品牌维度不受已选品牌影响。</p>
     *
     * @param dto 页面筛选参数
     * @return 两个维度的可选项
     */
    public MallFacetVO facets(MallFacetQueryDTO dto) {
        List<CategoryTreeVO> tree = categoryTreeOrEmpty();
        StoreGoodsSpuFacetQueryDTO query = new StoreGoodsSpuFacetQueryDTO();
        query.setKeyword(dto.getKeyword());
        query.setScopeCategoryIds(resolveAnchorIds(tree, dto.getCategoryId()));
        query.setFilterCategoryIds(resolveCategoryIds(tree, null, dto.getCategoryIds()));
        query.setFilterBrandIds(dto.getBrandIds());
        // 与分页同一套 C 端口径：facets 的命中数必须与列表口径一致，否则面板数字与列表对不上
        query.setShopStatus(SHOP_STATUS_APPROVED);
        query.setShelfStatus(SHELF_ON);
        query.setLockStatus(LOCK_OFF);

        StoreGoodsSpuFacetVO raw = BffFeignCall.call("store", DOWN_MSG, () -> storeClient.crossShopFacets(query));

        MallFacetVO vo = new MallFacetVO();
        vo.setBrands(toFacetItems(raw.getBrands()));
        vo.setCategories(dto.getCategoryId() == null
                ? rollupByTopCategory(tree, raw.getCategories())
                : toCategoryFacetItems(tree, raw.getCategories()));
        return vo;
    }

    /**
     * C 端商品详情。
     * <p><b>可见性口径与列表完全一致</b>（同一条不变量）：上架 + 未被平台锁定 + 店铺已审核通过，
     * 三者缺一即 404。⚠ 口径必须与 {@link #goods} 同进同退，否则会出现「列表里搜不到、
     * 却能靠直链打开」的商品（或反之），也会把未过审店铺 / 平台锁定商品漏到前台。</p>
     * <p>只调用<b>已有</b>的域接口（{@code platformStoreGoodsDetail} + {@code shopDetail}），
     * 不为 C 端新增域方法：域返回的是管理端超集，裁剪在 {@link #toMallDetail} 里做。</p>
     *
     * @param id 店铺商品 id
     * @return C 端详情（见 {@link MallGoodsDetailVO}）
     */
    public MallGoodsDetailVO detail(Long id) {
        StoreGoodsSpuPlatformDetailVO raw = platformDetailOrNull(id);
        // 短路顺序即不变量：先判商品自身（不存在 / 已下架 / 被锁定），再问店铺是否过审
        if (raw == null
                || !SHELF_ON.equals(raw.getShelfStatus())
                || !LOCK_OFF.equals(raw.getLockStatus())
                || !shopApproved(raw.getStoreId())) {
            throw notVisible();
        }
        return toMallDetail(raw);
    }

    /**
     * 取跨店商品详情；<b>业务 4xx（「商品不存在」等）收敛为 null</b>，其余异常原样抛出。
     * <p>⚠ 不能笼统地把 {@link ServiceException} 都当成「不存在」：{@link BffFeignCall} 对下游故障
     * 降级的也是 {@code ServiceException}（500 + 降级文案），一并吞掉会把「商品服务挂了」
     * 说成「商品已下架」，让故障伪装成正常业务结果。</p>
     *
     * @param id 店铺商品 id
     * @return 域详情；不存在等业务 4xx 时为 null
     */
    private StoreGoodsSpuPlatformDetailVO platformDetailOrNull(Long id) {
        try {
            return BffFeignCall.call("store", DOWN_MSG, () -> storeClient.platformStoreGoodsDetail(id));
        } catch (ServiceException e) {
            if (!isBusiness4xx(e)) {
                throw e;
            }
            log.warn("跨店商品详情查询未命中（业务 4xx），按不可见处理: id={}, msg={}", id, e.getMessage());
            return null;
        }
    }

    /**
     * 店铺是否「已审核通过」（C 端固定口径的一环）。
     * <p>店铺查询的业务 4xx（店铺不存在）同样按<b>不可见</b>处理——商品挂在一个查不到的店上，
     * 对顾客而言与已下架无异；下游故障仍原样抛出。</p>
     *
     * @param storeId 店铺 id（可空）
     * @return 已审核通过为 true
     */
    private boolean shopApproved(Long storeId) {
        if (storeId == null) {
            return false;
        }
        try {
            ShopVO shop = BffFeignCall.call("store", DOWN_MSG, () -> storeClient.shopDetail(storeId));
            return shop != null && SHOP_STATUS_APPROVED.equals(shop.getStatus());
        } catch (ServiceException e) {
            if (!isBusiness4xx(e)) {
                throw e;
            }
            log.warn("店铺查询未命中（业务 4xx），按不可见处理: storeId={}, msg={}", storeId, e.getMessage());
            return false;
        }
    }

    /** C 端「不可见」统一出口：不存在 / 已下架 / 被平台锁定 / 店铺未过审 一律同一个 404，不区分原因 */
    private ServiceException notVisible() {
        return new ServiceException(NOT_FOUND, NOT_VISIBLE_MSG);
    }

    /**
     * 判定是不是 {@link BffFeignCall} 原样透传的那类业务 4xx
     *
     * @param e 待判定异常
     * @return 400 / 403 / 404 之一为 true
     */
    private static boolean isBusiness4xx(ServiceException e) {
        Integer code = e.getCode();
        return code != null && (code == BAD_REQUEST || code == FORBIDDEN || code == NOT_FOUND);
    }

    // ---- 分类子树展开（域不持分类表，展开只能在端 BFF 做）----

    /**
     * 解析「待传给域的分类 id 集合」。
     * <p>优先级：已选分类（各自子树并集，去重）&gt; 路由锚点（该分类子树）&gt; 都不传 = 不按分类过滤。</p>
     * <p>⚠ <b>返回值永不为空集合</b>：域侧把空集合当成「不筛」，锚点/已选项解析不出子树时必须回退成
     * 该 id 自身（筛选变窄是可感知的降级，绝不放大成出全站）。</p>
     *
     * @param tree        分类树（可为空表 = 树不可用，此时不展开子树）
     * @param anchorId    路由锚点分类 id（可空）
     * @param selectedIds 页面已选分类 id（可空）
     * @return 待传给域的 id 集合；不按分类过滤时返回 null
     */
    private List<Long> resolveCategoryIds(List<CategoryTreeVO> tree, Long anchorId, List<Long> selectedIds) {
        if (selectedIds != null && !selectedIds.isEmpty()) {
            return expandSelected(tree, selectedIds);
        }
        if (anchorId != null) {
            return expandAnchor(tree, anchorId);
        }
        return null;
    }

    /**
     * 路由锚点的子树展开；树里查不到该分类（id 过期）或树不可用时，退化为只按该分类自身过滤
     *
     * @param tree     分类树
     * @param anchorId 锚点分类 id
     * @return 非空的分类 id 列表
     */
    private List<Long> expandAnchor(List<CategoryTreeVO> tree, Long anchorId) {
        Set<Long> ids = subtreeIds(tree, anchorId);
        if (ids == null || ids.isEmpty()) {
            log.warn("分类树中未找到分类 {}（可能已被删除）或分类树不可用，退化为按该分类自身过滤", anchorId);
            return List.of(anchorId);
        }
        return new ArrayList<>(ids);
    }

    /**
     * 已选分类的子树展开并集（去重）；单个 id 解析不出子树时回退成该 id 自身，而不是丢弃
     *
     * @param tree        分类树
     * @param selectedIds 已选分类 id（非空）
     * @return 非空的分类 id 列表
     */
    private List<Long> expandSelected(List<CategoryTreeVO> tree, List<Long> selectedIds) {
        Set<Long> union = new LinkedHashSet<>();
        for (Long id : selectedIds) {
            if (id == null) {
                continue;
            }
            Set<Long> ids = subtreeIds(tree, id);
            if (ids == null || ids.isEmpty()) {
                // 丢弃会让该选项「选中后无效果」，与页面表现不符；退化成按该 id 自身过滤
                log.warn("分类树中未找到已选分类 {} 或分类树不可用，退化为按该分类自身过滤", id);
                union.add(id);
            } else {
                union.addAll(ids);
            }
        }
        // 已选列表全为 null 的极端情况：按「不筛」处理，而不是传空集合（域侧空集 = 不筛，语义相同但更明确）
        return union.isEmpty() ? null : new ArrayList<>(union);
    }

    /**
     * 筛选锚点的范围（scope）id 集合；无锚点返回 null（不限范围）。
     * <p>与 {@link #resolveCategoryIds} 同一套「不放大范围」的降级规则。</p>
     *
     * @param tree     分类树
     * @param anchorId 锚点分类 id（可空）
     * @return 非空的分类 id 列表；无锚点时 null
     */
    private List<Long> resolveAnchorIds(List<CategoryTreeVO> tree, Long anchorId) {
        return anchorId == null ? null : expandAnchor(tree, anchorId);
    }

    /**
     * 在分类树中定位 id，收集其<b>自身与全部后代</b> id（纯函数，不做 IO）。
     *
     * @param tree 分类树（可为 null / 空表）
     * @param id   目标分类 id
     * @return 命中则返回 id 集合（至少含 id 自身）；树中无此节点返回 null
     */
    private Set<Long> subtreeIds(List<CategoryTreeVO> tree, Long id) {
        CategoryTreeVO node = findNode(tree, id);
        if (node == null) {
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        collectSelfAndDescendants(node, ids);
        return ids;
    }

    /**
     * 在分类树中递归查找节点
     *
     * @param nodes 分类树
     * @param id    目标分类 id
     * @return 命中的节点；未命中为 null
     */
    private CategoryTreeVO findNode(List<CategoryTreeVO> nodes, Long id) {
        if (nodes == null) {
            return null;
        }
        for (CategoryTreeVO node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            if (id.equals(node.getId())) {
                return node;
            }
            CategoryTreeVO found = findNode(node.getChildren(), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * 递归收集节点自身与全部后代 id（分类树由 goods-center 保证无环）
     *
     * @param node 当前节点（id 非空）
     * @param out  收集结果
     */
    private void collectSelfAndDescendants(CategoryTreeVO node, Set<Long> out) {
        out.add(node.getId());
        if (node.getChildren() == null) {
            return;
        }
        for (CategoryTreeVO child : node.getChildren()) {
            if (child != null && child.getId() != null) {
                collectSelfAndDescendants(child, out);
            }
        }
    }

    // ---- 搜索页分类 facet 的顶级上溯 ----

    /**
     * 把分类 facet 的每个分类上溯到<b>顶级祖先</b>，同祖先的命中数累加，名称取分类树里的权威名。
     * <p>搜索页没有路由锚点，若不归并则面板里会平铺出「T恤 / 牛仔裤 / 手机 …」这类深层分类；
     * 「按 count 降序、id 升序兜底」与域侧 facet 的排序承诺一致。</p>
     * <p><b>降级</b>：分类树不可用时原样映射（无从判断顶级归属，不猜）——面板退化为域名快照名，
     * 不影响可用性。树里查不到的分类按其自身归组（保留快照名）。</p>
     *
     * @param tree  分类树（可为 null / 空表）
     * @param items 域返回的分类 facet 项（域已按 count 降序）
     * @return C 端分类 facet 项（按 count 降序、id 升序）
     */
    private List<MallFacetItemVO> rollupByTopCategory(List<CategoryTreeVO> tree, List<StoreGoodsFacetItemVO> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        if (tree == null || tree.isEmpty()) {
            log.warn("分类树不可用，搜索页 facet 的分类维度退化为原样映射（不做顶级上溯）");
            return toFacetItems(items);
        }
        Map<Long, MallFacetItemVO> merged = new LinkedHashMap<>();
        for (StoreGoodsFacetItemVO item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            CategoryTreeVO top = topAncestor(tree, item.getId());
            Long topId = top == null ? item.getId() : top.getId();
            String topName = top == null ? item.getName() : top.getName();
            MallFacetItemVO acc = merged.get(topId);
            if (acc == null) {
                acc = new MallFacetItemVO();
                acc.setId(topId);
                acc.setName(topName);
                acc.setCount(0);
                merged.put(topId, acc);
            }
            acc.setCount(acc.getCount() + (item.getCount() == null ? 0 : item.getCount()));
        }
        List<MallFacetItemVO> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(MallFacetItemVO::getCount).reversed()
                .thenComparing(MallFacetItemVO::getId));
        return result;
    }

    /**
     * 找出 id 所在分支的顶级祖先（纯函数，不做 IO）
     *
     * @param tree 分类树
     * @param id   分类 id
     * @return 顶级祖先节点；树中无此分类为 null
     */
    private CategoryTreeVO topAncestor(List<CategoryTreeVO> tree, Long id) {
        if (tree == null) {
            return null;
        }
        for (CategoryTreeVO top : tree) {
            if (top == null || top.getId() == null) {
                continue;
            }
            if (containsNode(top, id)) {
                return top;
            }
        }
        return null;
    }

    /**
     * 判断以 node 为根的子树中是否含 id
     *
     * @param node 子树根
     * @param id   目标分类 id
     * @return 含则为 true
     */
    private boolean containsNode(CategoryTreeVO node, Long id) {
        if (node == null) {
            return false;
        }
        if (id.equals(node.getId())) {
            return true;
        }
        if (node.getChildren() == null) {
            return false;
        }
        for (CategoryTreeVO child : node.getChildren()) {
            if (containsNode(child, id)) {
                return true;
            }
        }
        return false;
    }

    // ---- C 端形状裁剪 ----

    /**
     * 域商品项 → C 端商品项（<b>逐字段手工映射</b>）。
     * <p>刻意不用 {@code BeanUtils.copyProperties}：域 VO 是「跨店通用」超集，含
     * {@code lockReason}/{@code lockUser}/{@code lockTime}/{@code goodsSpuId}/
     * {@code categoryPath}/{@code skuCount}/{@code updateTime} 等管理端或内部字段，
     * <b>域返回的字段不等于可以对外暴露</b>；手工映射保证域 VO 日后加字段不会自动漏到 C 端。</p>
     *
     * @param src 域商品项
     * @return C 端商品项
     */
    private MallGoodsItemVO toMallItem(StoreGoodsSpuCrossShopPageItemVO src) {
        MallGoodsItemVO vo = new MallGoodsItemVO();
        vo.setId(src.getId());
        vo.setName(src.getName());
        vo.setMainImage(src.getMainImage());
        vo.setMinPrice(src.getMinPrice());
        vo.setStoreId(src.getStoreId());
        vo.setStoreName(src.getStoreName());
        vo.setCategoryId(src.getCategoryId());
        vo.setCategoryName(src.getCategoryName());
        vo.setBrandId(src.getBrandId());
        vo.setBrandName(src.getBrandName());
        return vo;
    }

    /**
     * 域商品详情 → C 端商品详情（<b>逐字段手工映射</b>）。
     * <p>与 {@link #toMallItem} 同款理由：域出参是「跨店通用 / 管理端超集」，含
     * {@code lockStatus}/{@code lockReason}/{@code lockUser}/{@code lockTime}（平台锁定）、
     * {@code goodsSpuId}/{@code centerVersion}（中台关联与版本戳）、{@code shelfStatus}（内部状态）
     * 等管理端或内部字段，<b>域返回的字段不等于可以对外暴露</b>；手工映射保证域 VO 日后加字段
     * 不会自动漏到 C 端。</p>
     *
     * <p>{@code description} 另有一道<b>消毒</b>：它是店主自由录入的富文本（前端提示语即
     * 「支持 HTML」、库列注释为「商品详情（富文本）」），域侧原样存取、不清洗，故在这条出口上
     * 洗一遍再下发 —— 用的是 common 的 {@link HtmlSanitizer}，与 admin 端同一个出口口径、同一份
     * 白名单（见 [cross-cutting.md 第 21 条](/docs/contracts/cross-cutting.md)）。</p>
     *
     * @param src 域详情（已判定为对本端可见）
     * @return C 端详情
     */
    private MallGoodsDetailVO toMallDetail(StoreGoodsSpuPlatformDetailVO src) {
        MallGoodsDetailVO vo = new MallGoodsDetailVO();
        vo.setId(src.getId());
        vo.setName(src.getName());
        vo.setMainImage(src.getMainImage());
        vo.setImageList(src.getImageList());
        vo.setDescription(HtmlSanitizer.sanitizeRichText(src.getDescription()));
        vo.setSpecConfig(src.getSpecConfig());
        vo.setStoreId(src.getStoreId());
        // storeName 由域填充（域持 store_shop）；域没填时回退空串，前端按空值不渲染店铺行
        vo.setStoreName(src.getStoreName() == null ? "" : src.getStoreName());
        vo.setCategoryId(src.getCategoryId());
        vo.setCategoryName(src.getCategoryName());
        vo.setBrandId(src.getBrandId());
        vo.setBrandName(src.getBrandName());
        vo.setSkus(toMallSkus(src.getSkus()));
        return vo;
    }

    /**
     * 域 SKU 列表 → C 端 SKU 列表：<b>只保留上架的</b>——下架 SKU 不是「暂时缺货」，是店主没在卖，
     * 下发它只会让顾客选中一个买不到的规格。
     * <p>SPU 上架 ⟺ 至少一个 SKU 上架（域内不变量，由 {@code refreshShelfStatus} 维护），
     * 故走到这里（SPU 已判为上架）结果必非空。</p>
     *
     * @param skus 域 SKU 列表（可空）
     * @return C 端 SKU 列表（不可空）
     */
    private List<MallGoodsSkuVO> toMallSkus(List<StoreGoodsSkuVO> skus) {
        if (skus == null) {
            return new ArrayList<>();
        }
        return skus.stream()
                .filter(sku -> sku != null && SHELF_ON.equals(sku.getShelfStatus()))
                .map(this::toMallSku)
                .collect(Collectors.toList());
    }

    /**
     * 域 SKU → C 端 SKU（丢掉 {@code skuCode} 店主内部编码、{@code spuId} 内部归属、
     * {@code shelfStatus} 内部上下架状态）
     *
     * @param src 域 SKU
     * @return C 端 SKU
     */
    private MallGoodsSkuVO toMallSku(StoreGoodsSkuVO src) {
        MallGoodsSkuVO vo = new MallGoodsSkuVO();
        vo.setId(src.getId());
        vo.setSpecAttrs(src.getSpecAttrs());
        vo.setPrice(src.getPrice());
        vo.setMainImage(src.getMainImage());
        return vo;
    }

    /**
     * 分类页的分类 facet：scope 内的分类原样映射（<b>不做顶级上溯</b>），但名称优先取分类树的权威名。
     * <p>⚠ 域返回的名称是写入时的<b>快照</b>（`store_goods_spu.category_name`），分类改名后会过期；
     * 分类树是权威源。树降级为空表、或树中查不到该分类时，回退快照名。</p>
     *
     * @param tree  分类树（可为空表）
     * @param items 域 facet 项（可空）
     * @return C 端 facet 项（不可空）
     */
    private List<MallFacetItemVO> toCategoryFacetItems(List<CategoryTreeVO> tree, List<StoreGoodsFacetItemVO> items) {
        List<MallFacetItemVO> result = toFacetItems(items);
        if (tree == null || tree.isEmpty()) {
            return result;
        }
        for (MallFacetItemVO item : result) {
            if (item.getId() == null) {
                continue;
            }
            CategoryTreeVO node = findNode(tree, item.getId());
            if (node != null && node.getName() != null) {
                item.setName(node.getName());
            }
        }
        return result;
    }

    /**
     * 域 facet 项列表 → C 端 facet 项列表（原样映射三字段）
     *
     * @param items 域 facet 项（可空）
     * @return C 端 facet 项（不可空）
     */
    private List<MallFacetItemVO> toFacetItems(List<StoreGoodsFacetItemVO> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream().map(this::toFacetItem).collect(Collectors.toList());
    }

    /**
     * 域 facet 项 → C 端 facet 项
     *
     * @param src 域 facet 项
     * @return C 端 facet 项
     */
    private MallFacetItemVO toFacetItem(StoreGoodsFacetItemVO src) {
        MallFacetItemVO vo = new MallFacetItemVO();
        vo.setId(src.getId());
        vo.setName(src.getName());
        vo.setCount(src.getCount());
        return vo;
    }

    // ---- 降级 ----

    /**
     * 取分类树；<b>任何异常都吞掉并返回空表</b>。
     * <p>⚠ {@link BffFeignCall} 的语义是「抛 {@code ServiceException}」而不是「返回降级值」，
     * 所以「树拿不到就退化」不会自动发生：本方法就是那条退路。分类树对商品分页与 facets 只是
     * <b>增强</b>（子树展开、筛选名解析、顶级上溯），拿不到不应拖垮主流程——
     * 退化后不展开子树（按传入 id 自身过滤）、facet 分类维度原样映射。</p>
     *
     * @return 分类树；不可得时为空表（不是 null）
     */
    private List<CategoryTreeVO> categoryTreeOrEmpty() {
        try {
            List<CategoryTreeVO> tree = goodsCenterClient.categoryTree();
            return tree == null ? Collections.emptyList() : tree;
        } catch (Exception e) {
            log.warn("分类树获取失败，本次按无树降级（不展开子树、facet 原样输出）", e);
            return Collections.emptyList();
        }
    }
}
