package com.panoramic.storebff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 店铺端 BFF · 商家回复评价参数（<b>页面入参</b>，本层私有）。
 *
 * <p>⚠ <b>只有 {@code replyContent}</b>：店铺锚点（{@code storeId}）不在这里——它由
 * {@code StoreEvaluationBffService} 从登录态取（{@code type=store} 的 {@code loginUser.getId()}）
 * 后写进<b>域侧</b>入参 DTO；评价 id 走路径变量（资源标识）。</p>
 *
 * <p>⚠ <b>一条评价至多一个回复</b>，且回复后<b>不可改、不可删</b>（需求未提，属边界）：
 * 判据在域侧（条件更新 + 影响行数），本层<b>不重判</b>，域侧 400「该评价已回复」原样透传。</p>
 *
 * <p>约束<b>与域侧 {@code StoreGoodsEvaluationReplyDTO} 镜像</b>（非空、500 字）：
 * BFF 是页面边界，坏输入该在<b>这里</b>回 400，而不是穿到域里再经熔断语义绕一圈绕回来。</p>
 */
@Data
public class StoreEvaluationReplyDTO {

    /**
     * 回复内容（<b>纯文本</b>；前端按插值渲染、不许 {@code v-html}）
     */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容最多500字")
    private String replyContent;
}
