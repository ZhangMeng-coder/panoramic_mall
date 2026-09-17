package com.panoramic.mallbff.controller;

import com.panoramic.common.goods.vo.CategoryTreeVO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.vo.RespData;
import com.panoramic.mallbff.dto.MallFacetQueryDTO;
import com.panoramic.mallbff.dto.MallGoodsPageQueryDTO;
import com.panoramic.mallbff.service.CatalogBffService;
import com.panoramic.mallbff.vo.MallFacetVO;
import com.panoramic.mallbff.vo.MallGoodsItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端商品浏览接口（分类树 / 商品分页 / 筛选聚合）。
 * <p>全部为<b>公开</b>接口：前台首页公开、不要求登录（旅游客浏览商品），
 * 故三个路径都在网关侧与服务侧的免鉴权白名单内（见 docs/contracts/gateway.md 第三节）。</p>
 * <p>⚠ <b>C 端不接 RBAC</b>：本类没有、也不应有任何 {@code @PreAuthorize}——
 * 顾客没有角色/权限维度，「对自己可见的商品全可见」是预期状态，不是漏登记。
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
     */
    @PostMapping("/goods")
    public RespData<PageResult<MallGoodsItemVO>> goods(@RequestBody MallGoodsPageQueryDTO dto) {
        return RespData.success(catalogBffService.goods(dto));
    }

    /**
     * 筛选维度聚合（分类 / 品牌）
     */
    @PostMapping("/facets")
    public RespData<MallFacetVO> facets(@RequestBody MallFacetQueryDTO dto) {
        return RespData.success(catalogBffService.facets(dto));
    }
}
