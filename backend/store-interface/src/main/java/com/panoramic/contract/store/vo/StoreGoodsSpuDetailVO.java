package com.panoramic.contract.store.vo;

import com.panoramic.contract.store.dto.SpecConfigItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺在售商品详情 · 字段基础形状（store 域的详情出参字段集定义在此）。
 * <p>域内商品详情只有<b>一条</b>能力与<b>一个</b>出参类型（{@link StoreGoodsSpuPlatformDetailVO}，本类的超集），
 * 不再按端分两种详情：作用域由入参 {@code StoreGoodsSpuDetailQueryDTO.storeId} 决定</p>
 * <p>本类不含分类全路径（categoryPath）——域不持分类表，路径由端 BFF 读时解析后补在自己的出参上。</p>
 * <p>含平台锁定字段（lockStatus / lockReason / lockUser / lockTime）：域侧原样返回，
 * <b>「域返回了」不等于「可以对外下发」</b>——各端 BFF 输出前各自裁剪：
 * 商户端只展示锁定原因与时间（<b>不展示锁定人</b>，故其页面出参是逐字段手工映射、不继承本类），
 * C 端整体不暴露锁定信息。</p>
 */
@Data
public class StoreGoodsSpuDetailVO {

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
     * 锁定状态：0 未锁定，1 已锁定（平台锁定；锁定期 owner 侧整行只读）
     */
    private Integer lockStatus;

    /**
     * 锁定原因（owner 侧只读展示，不含锁定人）
     */
    private String lockReason;

    /**
     * 锁定人（UserType:UserId 原串，如 admin:1）。
     * <p>⚠ <b>商户端不展示锁定人</b>（仅管理端展示）；域侧照常返回，裁剪由各端 BFF 出口负责。</p>
     */
    private String lockUser;

    /**
     * 锁定时间
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
}
