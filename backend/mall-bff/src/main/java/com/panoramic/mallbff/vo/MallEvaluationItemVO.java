package com.panoramic.mallbff.vo;

import com.panoramic.contract.store.vo.StoreGoodsEvaluationSkuVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价列表项（<b>本端私有类型</b>，{@code POST /evaluations/page} 的出参元素）。
 *
 * <p>⚠ 它与域契约 {@code com.panoramic.contract.store.vo.StoreGoodsEvaluationPageItemVO}
 * <b>不是一份东西</b>，这是「同一份形状造第二个出口」的<b>正当理由</b>（与 {@link MallGoodsItemVO}
 * 对域商品项同款）：<b>裁剪 + 补齐</b>两件事都发生了——</p>
 * <ul>
 *   <li><b>裁剪</b>：域出参带 {@code customerId}（评价人账号 id，内部标识）与 {@code spuName}
 *       （C 端评价区长在商品详情页里，商品名页面已知），两者都<b>不下发</b>。
 *       域 VO 日后加字段不会自动漏到页面，故映射<b>逐字段手工写</b>、不用 {@code BeanUtils}。</li>
 *   <li><b>补齐</b>：把 {@code customerId} 换成 {@link #nickname} / {@link #avatar}——
 *       顾客资料在 customer-center 域，store 域不持、也不跨域去取。</li>
 * </ul>
 *
 * <p>⚠ <b>评价文字与商家回复都按纯文本渲染</b>（前端 {@code {{ }}} 插值、<b>不许 {@code v-html}</b>）：
 * 域侧原样存取、本层<b>不接</b> {@code HtmlSanitizer}——商品详情那条链路的清洗是为「店主自由录入 HTML」
 * 这个前提存在的，评价 / 回复没有这个前提（见 cross-cutting 第 21 条）。</p>
 */
@Data
public class MallEvaluationItemVO {

    /**
     * 评价 id（页面用它做列表 key / 分页去重，也是后续「商家回复」的定位键）
     */
    private Long id;

    /**
     * 被评价的商品 SPU id（= 本页所属商品；前端按它归组「一行一个商品的评价」）
     */
    private Long spuId;

    /**
     * 下单时的 SKU 快照（该 SPU 在本单里的全部 SKU 行组：规格组合 / 单价 / 数量）
     * <p>原样透传域类型 {@link StoreGoodsEvaluationSkuVO}（入出参共用一份形状）——本层无字段可裁可补，
     * 不另造本端类型。</p>
     */
    private List<StoreGoodsEvaluationSkuVO> skuSnapshot;

    /**
     * 评分：1 ~ 5 星
     */
    private Integer score;

    /**
     * 评价文字；未填写为 {@code null}
     */
    private String content;

    /**
     * 评价人展示名
     * <p>⚠ <b>拿不到资料时统一为占位「用户」</b>——<b>不拼手机号后 4 位</b>：手机号只在
     * {@code mall_user}（本端独有），商户端拿不到，「用户 + 后 4 位」两处各写一份必然漂移；
     * 那条规则只在<b>写入侧</b>实现一处（注册 / 改资料的默认昵称，见 {@code CustomerProfileBffService}）。</p>
     */
    private String nickname;

    /**
     * 评价人头像 URL；无资料或未设置头像为 {@code null}（前端回退默认头像）
     */
    private String avatar;

    /**
     * 评价时间（域侧固定按它倒序下发）
     */
    private LocalDateTime createTime;

    /**
     * 商家回复内容；{@code null} = 未回复
     */
    private String replyContent;

    /**
     * 商家回复时间；{@code null} = 未回复
     */
    private LocalDateTime replyTime;
}
