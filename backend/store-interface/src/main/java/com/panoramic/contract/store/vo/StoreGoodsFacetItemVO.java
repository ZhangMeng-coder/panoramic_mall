package com.panoramic.contract.store.vo;

import lombok.Data;

/**
 * 筛选维度的一个可选项（分类 / 品牌通用）
 * <p>{@code name} 取自库中<b>快照</b>（同一 id 快照名一致）。端 BFF 可用权威源覆盖：
 * 分类名与顶级归属由 BFF 用分类树解析，树里查不到时回退本字段。</p>
 */
@Data
public class StoreGoodsFacetItemVO {

    /** 维度值 id（分类 id 或品牌 id） */
    private Long id;

    /** 维度值名称（快照） */
    private String name;

    /** 该维度值下的命中商品数 */
    private Integer count;
}
