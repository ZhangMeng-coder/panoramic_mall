package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.common.store.dto.StoreGoodsLockDTO;
import com.panoramic.common.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuPlatformPageQueryDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.common.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.StoreGoodsSpuDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.common.store.vo.StoreGoodsSpuPlatformPageItemVO;
import com.panoramic.store.entity.StoreGoodsSpu;

/**
 * 店铺在售商品（SPU）服务（store 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口承载店铺商品的
 * 上下架联动、SKU 锁定规则与平台锁定能力（见下）。</p>
 * <p><b>owner 侧</b>（store-bff 调用）：方法必带 {@code storeId}，只作用于 {@code store_id == storeId}
 * 的行（R11）；SKU 侧操作先校验其 SPU 归属。<b>platform 侧</b>（admin BFF 调用）：方法不带 storeId、
 * 跨店全量（{@link #platformPage} / {@link #platformDetail} / {@link #lock} / {@link #unlock}）。
 * 两侧的分流由端 BFF 选择调哪一侧方法决定，域内不做身份判断。</p>
 * <p>规则口径：
 * <ul>
 *   <li>R1 新增：SPU 与全部 SKU 一律以下架态落库；每个 SKU 价格必填且 ≥0.01；</li>
 *   <li>R2/R3 联动：上架任一 SKU → SPU 自动上架；SKU 全下架 → SPU 自动下架
 *       （不变量 {@code SPU上架 ⟺ ≥1 SKU 上架}，故 SPU 无独立上下架入口）；</li>
 *   <li>R4/R5：已上架 SKU 整行锁死（不可改、不可删），未上架 SKU 可增可改可删；</li>
 *   <li>R6：存在上架 SKU 时 {@code spec_config} 不可改；</li>
 *   <li>R8：存在上架 SKU 时拒绝删 SPU；否则软删并级联软删其下全部 SKU；</li>
 *   <li>R12 平台锁定：锁定把名下 SKU 全部级联下架、SPU 随之推导为下架（推导规则不变）；
 *       锁定期 owner 侧的改/删/改 SKU/上下架 一律拒绝（整行只读）；仅平台可解锁，
 *       解锁不自动恢复上架（由店主手动重新上架）。</li>
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

    // ---- platform 侧（admin BFF 调用，不带 storeId，跨店全量）----

    /**
     * 店铺商品分页（跨店全量）：分类为多值子树匹配（{@code categoryIds} 由端 BFF 用分类树展开后传入），
     * 可再按品牌 / 店铺 / 上下架 / 锁定状态 / 名称关键字筛选；回填店铺名与 SKU 数量。
     * <p>出参的 {@code categoryPath} 不在此填充（域不持分类表），由端 BFF 读时解析。</p>
     *
     * @param dto 分页/筛选参数
     * @return 分页结果
     */
    PageResult<StoreGoodsSpuPlatformPageItemVO> platformPage(StoreGoodsSpuPlatformPageQueryDTO dto);

    /**
     * 店铺商品详情（跨店，不校验归属；含 SKU 列表与锁定信息），额外回填所属店铺名。
     * <p>{@code categoryPath} 同样由端 BFF 读时解析。</p>
     *
     * @param id 店铺商品 id
     * @return 商品详情（平台侧）
     */
    StoreGoodsSpuPlatformDetailVO platformDetail(Long id);

    /**
     * 锁定商品（平台）：写锁定状态/原因/锁定人/锁定时间，并把名下 SKU 全部级联下架，
     * 再由 {@code refreshDerived} 推导 SPU 为下架。已锁定则拒绝重复操作。
     * <p>锁定期 owner 侧整行只读，仅本方法可解。</p>
     *
     * @param id  店铺商品 id
     * @param dto 锁定请求（原因必填）
     */
    void lock(Long id, StoreGoodsLockDTO dto);

    /**
     * 解锁商品（平台）：清空锁定字段。<b>不恢复上架</b>——SKU 保持下架，需店主手动重新上架。
     * 未锁定则拒绝。
     *
     * @param id 店铺商品 id
     */
    void unlock(Long id);
}
