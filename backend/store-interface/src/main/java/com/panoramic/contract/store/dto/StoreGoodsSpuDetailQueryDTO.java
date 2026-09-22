package com.panoramic.contract.store.dto;

import lombok.Data;

/**
 * 店铺商品详情查询入参（store 域内部接口与各端 BFF 同源共享）。
 * <p>详情的作用域**只此一项**：路径变量 {@code id} 是资源标识、不并入 DTO（cross-cutting 第 23 条），
 * 故本 DTO 只放作用域字段。</p>
 * <p>⚠ {@code storeId} <b>可空</b>——该能力存在合法全量视角（管理端 / C 端跨店详情不限定店铺）；
 * <b>传了就按它筛，没传就是不限定</b>，域内不判身份、不按端分流（第 22 条）。值由调用方端 BFF
 * 自登录态取（店铺端传 {@code LoginUser.getId()}），**禁止**从前端入参透传。</p>
 */
@Data
public class StoreGoodsSpuDetailQueryDTO {

    /**
     * 所属店铺 id（= 店主账号 id）；可空 = 不限定店铺（跨店详情）
     */
    private Long storeId;
}
