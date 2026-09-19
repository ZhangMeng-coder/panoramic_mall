package com.panoramic.mallbff.controller;

import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.vo.RespData;
import com.panoramic.mallbff.dto.MallFacetQueryDTO;
import com.panoramic.mallbff.dto.MallGoodsPageQueryDTO;
import com.panoramic.mallbff.service.CatalogBffService;
import com.panoramic.mallbff.vo.MallFacetVO;
import com.panoramic.mallbff.vo.MallGoodsDetailVO;
import com.panoramic.mallbff.vo.MallGoodsItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端商品浏览接口（分类树 / 商品分页 / 筛选聚合 / 商品详情）。
 * <p><b>鉴权分级</b>：只有 {@code GET /catalog/categories}（首页宫格的分类树）是<b>公开</b>的，
 * 与本模块白名单里的 {@code /catalog/categories} 精确对应；<b>商品分页 / 筛选 / 详情一律需登录态</b>
 * （网关验 JWT + Redis，见 docs/contracts/gateway.md 第三节）。⚠ 白名单是<b>精确路径</b>而非
 * {@code /catalog/**} 前缀——写成前缀会把商品查询与详情一起放开到公网。</p>
 * <p>⚠ <b>C 端不接 RBAC</b>：本类没有、也不应有任何 {@code @PreAuthorize}——
 * 顾客没有角色/权限维度，「已登录顾客对自己可见的商品全可见」是预期状态，不是漏登记。
 * 展示口径（已过审店铺 + 上架 + 未锁定）由 {@link CatalogBffService} 固定在 BFF，
 * 不由前端传参决定。</p>
 * <p>分页与 facets 用 {@code POST + @RequestBody}：入参含分类/品牌集合，走 query string 会被
 * axios 序列化成 {@code categoryIds[]=1} 形状，POST + body 规避（与 store 域内部接口同款理由）。</p>
 */
@RestController
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogBffService catalogBffService;

    /**
     * 全量分类树（首页宫格 / 分类页标题 / 分类筛选名解析共用）
     */
    @GetMapping("/categories")
    public RespData<List<CategoryTreeVO>> categories() {
        return RespData.success(catalogBffService.categories());
    }

    /**
     * 商品分页（C 端展示口径由 BFF 固定）
     * <p>⚠ 本接口<b>需登录态</b>，但登录顾客提交的分页参数仍是不可信输入，必须在 BFF 侧先过
     * {@code BasePageVO} 的默认组约束：repo 的分页插件未设 {@code maxLimit}，
     * 无上限的 {@code pageSize} 会原样直达域侧；缺 {@code pageSize} 还会在域侧拆箱成 NPE，
     * 被降级文案报成「下游故障」——两个方向都得在入口拦掉。</p>
     * <p>⚠ 这里必须是<b>裸 {@code @Valid}</b>：{@code BasePageVO} 的约束不带 groups、落在默认组，
     * 而 {@code ValidationGroups} 的组接口是裸接口、不继承 {@code Default}，
     * 换 {@code @Validated(组.class)} 会永远不触发，等于没加。</p>
     */
    @PostMapping("/goods")
    public RespData<PageResult<MallGoodsItemVO>> goods(@Valid @RequestBody MallGoodsPageQueryDTO dto) {
        return RespData.success(catalogBffService.goods(dto));
    }

    /**
     * 筛选维度聚合（分类 / 品牌）
     */
    @PostMapping("/facets")
    public RespData<MallFacetVO> facets(@RequestBody MallFacetQueryDTO dto) {
        return RespData.success(catalogBffService.facets(dto));
    }

    /**
     * 商品详情（<b>需登录态</b>：不在免鉴权白名单里）
     * <p>不可见的四种情形——不存在 / 已下架 / 被平台锁定 / 店铺未过审——一律回业务码 <b>404</b>
     * 「商品不存在或已下架」，<b>不区分原因</b>（也不泄露商品存在性）。可见性口径与
     * {@link #goods} 完全一致，见 {@code CatalogBffService#detail}。</p>
     */
    @GetMapping("/goods/{id}")
    public RespData<MallGoodsDetailVO> detail(@PathVariable("id") Long id) {
        return RespData.success(catalogBffService.detail(id));
    }
}
