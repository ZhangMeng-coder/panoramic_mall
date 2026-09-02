package com.panoramic.goods.service;

import com.panoramic.goods.dto.SpuPageQueryDTO;
import com.panoramic.goods.dto.SpuSaveDTO;
import com.panoramic.goods.dto.SpuStatusDTO;
import com.panoramic.goods.dto.SpuUpdateDTO;
import com.panoramic.goods.vo.PageResult;
import com.panoramic.goods.vo.SpuDetailVO;
import com.panoramic.goods.vo.SpuPageItemVO;

/**
 * 商品（SPU）服务
 */
public interface SpuService {

    /**
     * 商品分页查询（分类/品牌/状态/名称关键字筛选）
     *
     * @param dto 分页查询参数
     * @return 分页结果（含分类名/品牌名）
     */
    PageResult<SpuPageItemVO> page(SpuPageQueryDTO dto);

    /**
     * 商品详情（含 SKU 列表）
     *
     * @param id 商品ID
     * @return 商品详情
     */
    SpuDetailVO detail(Long id);

    /**
     * 新建商品（SPU + SKU 级联保存）
     *
     * @param dto 新建请求
     * @return 商品ID
     */
    Long saveSpu(SpuSaveDTO dto);

    /**
     * 更新商品（SPU + SKU diff 级联更新：无 id 的新 SKU 插入，带 id 的更新，
     * 存量中缺失的 SKU 逻辑删除，保证 SKU ID 稳定）
     *
     * @param id  商品ID
     * @param dto 更新请求
     */
    void updateSpu(Long id, SpuUpdateDTO dto);

    /**
     * 商品上下架
     *
     * @param id  商品ID
     * @param dto 状态请求（0 下架，1 上架）
     */
    void updateStatus(Long id, SpuStatusDTO dto);

    /**
     * 删除商品（上架中拒绝；级联逻辑删除全部 SKU）
     *
     * @param id 商品ID
     */
    void deleteSpu(Long id);
}
