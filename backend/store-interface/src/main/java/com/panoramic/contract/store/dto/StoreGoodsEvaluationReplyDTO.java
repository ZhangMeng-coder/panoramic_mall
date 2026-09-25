package com.panoramic.contract.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家回复评价（store 域内部接口与端 BFF 同源共享）。
 * <p>一条评价<b>至多一条回复</b>（回复列就在评价行上，不另立回复表），故没有「修改回复」「删除回复」
 * 一说：回复一次即成定局。评价人也不能再回（C 端无追评能力）。</p>
 * <p>⚠ {@code storeId} 是数据作用域（商户只能回复本店评价），值由 store-bff 自登录态取，
 * <b>禁止</b>从前端入参透传。它是本 DTO 的必填锚点，用<b>默认组</b> {@code @NotNull}
 * （本 DTO 不是任何端的页面入参类型，不需要 {@code StoreScopeGroup} 分档，见 store.md 第三节）。</p>
 */
@Data
public class StoreGoodsEvaluationReplyDTO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id）
     */
    @NotNull(message = "店铺ID不能为空")
    private Long storeId;

    /**
     * 回复内容（纯文本）
     */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容最多500字")
    private String replyContent;
}
