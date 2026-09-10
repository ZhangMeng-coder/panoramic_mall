package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.store.entity.StoreGoodsSpu;

/**
 * 店铺在售商品（SPU）服务（store 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口承载店铺商品的
 * 上下架联动与 SKU 锁定规则（见下）。全部方法均为 <b>owner 侧</b>：必带 {@code storeId}，
 * 只作用于 {@code store_id == storeId} 的行（R11）；SKU 侧操作先校验其 SPU 归属。
 * 本切片无 platform 侧对应物（admin 不做店铺商品管理）。</p>
 * <p>规则口径：
 * <ul>
 *   <li>R1 新增：SPU 与全部 SKU 一律以下架态落库；每个 SKU 价格必填且 ≥0.01；</li>
 *   <li>R2/R3 联动：上架任一 SKU → SPU 自动上架；SKU 全下架 → SPU 自动下架
 *       （不变量 {@code SPU上架 ⟺ ≥1 SKU 上架}，故 SPU 无独立上下架入口）；</li>
 *   <li>R4/R5：已上架 SKU 整行锁死（不可改、不可删），未上架 SKU 可增可改可删；</li>
 *   <li>R6：存在上架 SKU 时 {@code spec_config} 不可改；</li>
 *   <li>R8：存在上架 SKU 时拒绝删 SPU；否则软删并级联软删其下全部 SKU。</li>
 * </ul>
 * <b>域内不做审核门禁</b>：店铺 {@code status == 2} 的判断由端 BFF 编排时前置（R9）。</p>
 */
public interface StoreGoodsSpuService extends IService<StoreGoodsSpu> {

    /**
     * 我的商品分页（仅 storeId 名下的商品，回填 SKU 数量）
     *
     * @param storeId 店主账号 id（== 店铺主键）
     * @param dto     分页/筛选参数
     * @return 分页结果
     */
    PageResult<StoreGoodsSpuPageItemVO> page(Long storeId, StoreGoodsSpuPageQueryDTO dto);

    /**
     * 我的商品详情（含 SKU 列表）。
     * <p>仅返回店铺侧字段；中台版本比对结果由 store-bff 编排时补充（纯域不调中台）。</p>
     *
     * @param storeId 店主账号 id
     * @param id      店铺商品 id
     * @return 商品详情
     */
    StoreGoodsSpuDetailVO detail(Long storeId, Long id);

    /**
     * 新增商品（可一并落 SKU；SPU 与 SKU 均以下架态起步，price 必填）
     *
     * @param storeId 店主账号 id
     * @param dto     新增请求
     * @return 新商品 id
     */
    Long save(Long storeId, StoreGoodsSpuSaveDTO dto);

    /**
     * 修改商品（仅基础信息 + 规格属性配置，不涉及 SKU 行；SKU 由 replaceSkus 维护）。
     * <p>存在上架 SKU 时规格配置只读（R6）；{@code centerVersion} 仅店主点过「同步」后携带，
     * 未携带则保持原值。上下架状态不接受传入（由 SKU 联动推导）。</p>
     *
     * @param storeId 店主账号 id
     * @param id      店铺商品 id
     * @param dto     修改请求
     */
    void update(Long storeId, Long id, StoreGoodsSpuUpdateDTO dto);

    /**
     * 删除商品：存在上架 SKU 时拒绝（R8）；否则软删 SPU 并级联软删其下全部 SKU
     *
     * @param storeId 店主账号 id
     * @param id      店铺商品 id
     */
    void delete(Long storeId, Long id);

    /**
     * SKU 整单替换：无 id 者新增（下架态），带 id 者更新，存量缺失者删除。
     * <p>已上架 SKU 必须原样保留（缺行即拒绝）且字段不得变更（R4）；删除仅限未上架（R5）。
     * 替换后按最新 SKU 状态重新联动 SPU 上下架。空列表 = 清空全部 SKU。</p>
     *
     * @param storeId 店主账号 id
     * @param id      店铺商品 id
     * @param dto     全量替换请求
     */
    void replaceSkus(Long storeId, Long id, StoreGoodsSkuReplaceDTO dto);

    /**
     * SKU 上下架（不受 SPU 状态限制），并反向联动 SPU（R2/R3）
     *
     * @param storeId     店主账号 id
     * @param spuId       店铺商品 id（校验 SKU 归属）
     * @param skuId       SKU id
     * @param shelfStatus 0 下架，1 上架
     */
    void updateSkuShelf(Long storeId, Long spuId, Long skuId, Integer shelfStatus);
}
