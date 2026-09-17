package com.panoramic.mallbff.vo;

import lombok.Data;

import java.util.List;

/**
 * C 端筛选维度聚合结果（页面契约，见 docs/contracts/mall-bff.md）。
 * <p>域的 {@code StoreGoodsSpuFacetVO} 与本形状同构但**归属不同**（域 VO 是跨店通用超集、
 * 管理端也在用），故 C 端另立一份，避免日后域 VO 加字段直接漏到 C 端。</p>
 * <p><b>分类维度的口径按页面而变</b>：分类页（带路由锚点）原样返回锚点子树内的分类；
 * 搜索页（无锚点）由 {@code CatalogBffService} 把每个分类上溯到<b>顶级祖先</b>并合并命中数——
 * 搜索页筛选面板只展示一级分类。</p>
 */
@Data
public class MallFacetVO {

    /**
     * 分类维度可选项
     */
    private List<MallFacetItemVO> categories;

    /**
     * 品牌维度可选项
     */
    private List<MallFacetItemVO> brands;
}
