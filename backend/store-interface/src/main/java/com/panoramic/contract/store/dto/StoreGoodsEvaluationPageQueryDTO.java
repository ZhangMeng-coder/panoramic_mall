package com.panoramic.contract.store.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 评价分页查询参数（store 域内部接口与端 BFF 同源共享）。
 * <p><b>跨店通用</b>（无作用域锚点、限定条件全由调用方自设，与商品跨店分页同款）：
 * <ul>
 *   <li>mall-bff（C 端商品详情页的评价区）：传 {@code spuId}——「这个商品的评价」；</li>
 *   <li>store-bff（商户端评价页）：传 {@code storeId}（自登录态）——「我店铺的全部评价」，
 *       并可再按 {@code spuId}（按商品筛选）与 {@code scores}（星级筛选）收窄。</li>
 * </ul>
 * 域内只做「传了就按它筛」，不判身份、不做端别分流；两个条件同传即取交集（本店该商品的评价）。
 * 都不传 = 全量（当前无人这么用，但形状上合法）。</p>
 * <p>⚠ 用 {@code POST + @RequestBody} 而非 query 参数：{@code scores} 是集合，
 * {@code @SpringQueryMap} 对集合字段的序列化口径不确定（与 {@code pageStoreGoodsCrossShop} 同因）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsEvaluationPageQueryDTO extends BasePageVO {

    /**
     * 作用域：所属店铺 id（= 店主账号 id）；空 = 不限定店铺。
     * <p>值由端 BFF 自登录态取（店铺端传 {@code LoginUser.getId()}），<b>禁止</b>从前端入参透传。</p>
     */
    private Long storeId;

    /**
     * 商品 SPU id；空 = 不限定商品（C 端商品详情页必传）
     */
    private Long spuId;

    /**
     * 星级筛选（多值，如 {@code [1, 2]} = 只看 1 星与 2 星）；空 = 全部星级。
     * <p>⚠ 只服务商户端（按星级筛评价）；<b>C 端不提供星级筛选</b>（它要看的是星级分布，
     * 见 {@code StoreGoodsEvaluationStatVO}）——但域侧不为此分侧，传了就筛。</p>
     */
    private List<Integer> scores;
}
