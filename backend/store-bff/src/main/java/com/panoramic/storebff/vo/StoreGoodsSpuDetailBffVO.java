package com.panoramic.storebff.vo;

import com.panoramic.contract.goods.vo.SpuDetailVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuDetailVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺端 BFF · 店铺商品详情（页面态模型）。
 * <p>在 store 域出参 {@link StoreGoodsSpuDetailVO} 之上，补充两类「只能由 BFF 编排得到」的字段：
 * <ul>
 *   <li>中台关联是否已过期：store 域是纯域、不调中台，故版本比对只能在 BFF 完成；</li>
 *   <li>分类全路径：域不持分类表，路径由本层读时调 goods-center 批量路径接口解析（解析失败留空）。</li>
 * </ul>
 * 这几项都不下沉到 common。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreGoodsSpuDetailBffVO extends StoreGoodsSpuDetailVO {

    /**
     * 中台模板是否已更新：本商品关联了中台 SPU，且中台当前版本戳 != 落库的 center_version。
     * true 时前端展示「同步」按钮（覆盖 / 不覆盖由店主决定，不阻断保存）。
     */
    private Boolean centerOutdated;

    /**
     * 关联的中台模板已不存在（已删除）或中台不可达：不报错，仅提示前端可解除关联
     */
    private Boolean centerMissing;

    /**
     * 中台模板快照，供「同步」按钮把中台当前内容覆盖进表单；缺失/不可达时为 null
     */
    private SpuDetailVO centerSpu;

    /**
     * 分类全路径（如「服饰 / 男装 / T恤」），本层读时解析；解析失败为空，前端回退快照 {@code categoryName}
     */
    private String categoryPath;
}
