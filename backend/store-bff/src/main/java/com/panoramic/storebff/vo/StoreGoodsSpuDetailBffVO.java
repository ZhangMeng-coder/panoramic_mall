package com.panoramic.storebff.vo;

import com.panoramic.contract.goods.vo.SpuDetailVO;
import com.panoramic.contract.store.dto.SpecConfigItem;
import com.panoramic.contract.store.vo.StoreGoodsSkuVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺端 BFF · 店铺商品详情（页面态模型）。
 * <p><b>刻意不继承域出参</b>（域出参是管理端超集 {@code StoreGoodsSpuPlatformDetailVO}）：
 * 本类的字段是<b>逐条声明</b>的商户端下发面——域返回的字段不等于可以对外暴露
 * （见 docs/contracts/store.md 第三节、cross-cutting 第 17/19/20 条）。由此守住两条既有口径：
 * <ul>
 *   <li>{@code lockUser}（锁定人）<b>不下发到商户端</b>——商户端只展示锁定原因与时间，锁定人仅管理端可见；</li>
 *   <li>域出参里的 {@code storeName} 不回填（那是跨店视角才需要的字段，商户端的店就是自己）。</li>
 * </ul>
 * <p>另补两类「只能由 BFF 编排得到」的字段：
 * <ul>
 *   <li>中台关联是否已过期：store 域是纯域、不调中台，故版本比对只能在 BFF 完成；</li>
 *   <li>分类全路径：域不持分类表，路径由本层读时调 goods-center 批量路径接口解析（解析失败留空）。</li>
 * </ul>
 * 这几项都不下沉到 common。</p>
 */
@Data
public class StoreGoodsSpuDetailBffVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 所属店铺 id（= 店主账号 id）
     */
    private Long storeId;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 所属分类ID（引用中台 goods_category）
     */
    private Long categoryId;

    /**
     * 分类名称快照
     */
    private String categoryName;

    /**
     * 品牌ID（引用中台 goods_brand）
     */
    private Long brandId;

    /**
     * 品牌名称快照
     */
    private String brandName;

    /**
     * 主图 URL
     */
    private String mainImage;

    /**
     * 轮播图 URL 列表
     */
    private List<String> imageList;

    /**
     * 商品详情（富文本 HTML）
     */
    private String description;

    /**
     * 规格属性配置
     */
    private List<SpecConfigItem> specConfig;

    /**
     * 上下架：0 下架，1 上架（由 SKU 联动推导）
     */
    private Integer shelfStatus;

    /**
     * 锁定状态：0 未锁定，1 已锁定（平台锁定；锁定期整行只读）
     */
    private Integer lockStatus;

    /**
     * 锁定原因（商户端只读展示）
     */
    private String lockReason;

    /**
     * 锁定时间（商户端只读展示）。
     * <p>⚠ 锁定人（{@code lockUser}）<b>刻意不在此列</b>：仅管理端展示，商户端不出这个字段。</p>
     */
    private LocalDateTime lockTime;

    /**
     * 中台关联 SPU id（null = 未关联中台）
     */
    private Long goodsSpuId;

    /**
     * 上次关联/同步时中台 SPU 的版本戳（null = 未关联中台）
     */
    private Long centerVersion;

    /**
     * SKU 列表
     */
    private List<StoreGoodsSkuVO> skus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

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
