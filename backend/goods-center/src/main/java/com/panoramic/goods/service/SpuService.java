package com.panoramic.goods.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.common.goods.dto.SpuPageQueryDTO;
import com.panoramic.common.goods.dto.SpuSaveDTO;
import com.panoramic.common.goods.dto.SpuSkuReplaceDTO;
import com.panoramic.common.goods.dto.SpuStatusDTO;
import com.panoramic.common.goods.dto.SpuUpdateDTO;
import com.panoramic.goods.entity.GoodsSpu;
import com.panoramic.common.goods.vo.PageResult;
import com.panoramic.common.goods.vo.SpuBySkuCodeVO;
import com.panoramic.common.goods.vo.SpuDetailVO;
import com.panoramic.common.goods.vo.SpuPageItemVO;

/**
 * 商品（SPU）服务
 */
public interface SpuService extends IService<GoodsSpu> {

    /**
     * 商品分页查询（分类/品牌/状态/名称关键字筛选）
     *
     * @param dto 分页查询参数
     * @return 分页结果（含分类名/品牌名）
     */
    PageResult<SpuPageItemVO> page(SpuPageQueryDTO dto);

    /**
     * 商品详情（含 SKU 列表、规格属性配置、分类完整链条）
     *
     * @param id 商品ID
     * @return 商品详情
     */
    SpuDetailVO detail(Long id);

    /**
     * 新建商品（基础信息 + 规格属性配置；不携带 SKU，SKU 由 replaceSkus 单独维护）
     *
     * @param dto 新建请求
     * @return 商品ID
     */
    Long saveSpu(SpuSaveDTO dto);

    /**
     * 更新商品（仅基础信息 + 规格属性配置；对仍被存量 SKU 使用的规格/属性值做防孤立守卫，
     * 不涉及 SKU 行的变更）
     *
     * @param id  商品ID
     * @param dto 更新请求
     */
    void updateSpu(Long id, SpuUpdateDTO dto);

    /**
     * 全量替换商品 SKU（规格管理专用）：校验入参组合必须匹配该商品已存的规格属性配置；
     * 无 id 的新 SKU 插入，带 id 的更新，存量中缺失的 SKU 逻辑删除，保证 SKU ID 稳定。
     * 空 skus 表示清空该商品全部 SKU
     *
     * @param id  商品ID
     * @param dto 全量替换请求
     */
    void replaceSkus(Long id, SpuSkuReplaceDTO dto);

    /**
     * 商品展示/隐藏切换（1 展示，0 隐藏）
     *
     * @param id  商品ID
     * @param dto 状态请求（0 隐藏，1 展示）
     */
    void updateStatus(Long id, SpuStatusDTO dto);

    /**
     * 删除商品（展示中拒绝；级联逻辑删除全部 SKU）
     *
     * @param id 商品ID
     */
    void deleteSpu(Long id);

    /**
     * 按 SKU 编码反查所属标准商品（店铺端「填 SKU_CODE 预填新增表单」用）。
     * 未命中返回 spu=null（不抛异常，店铺端允许「查不到照样自建」）；
     * 编码重复时取 SKU id 最小者并置 matchedSkuCount &gt; 1
     *
     * @param skuCode SKU 编码
     * @return 查询结果（含匹配条数）
     */
    SpuBySkuCodeVO findBySkuCode(String skuCode);

    /**
     * 统计某分类下挂载的商品数（删除分类守卫用）
     *
     * @param categoryId 分类ID
     * @return 商品数
     */
    long countByCategoryId(Long categoryId);

    /**
     * 统计某品牌下挂载的商品数（删除品牌守卫用）
     *
     * @param brandId 品牌ID
     * @return 商品数
     */
    long countByBrandId(Long brandId);
}
