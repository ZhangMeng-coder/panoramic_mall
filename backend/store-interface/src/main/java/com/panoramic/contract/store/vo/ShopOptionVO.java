package com.panoramic.contract.store.vo;

import lombok.Data;

/**
 * 店铺下拉选项（管理后台「店铺商品管理」按店铺筛选用）
 * <p>只为下拉搬运 id + 名称，避免把全量 {@link ShopVO}（含资质、审核信息）带到页面上。
 * 不按审核状态过滤：未审核通过的店铺本就没有商品，过滤无收益。</p>
 */
@Data
public class ShopOptionVO {

    /**
     * 店铺 id（= 店主账号 id）
     */
    private Long id;

    /**
     * 店铺名称
     */
    private String shopName;
}
