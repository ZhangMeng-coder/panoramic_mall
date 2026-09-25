package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.store.dto.StoreGoodsLockDTO;
import com.panoramic.contract.store.dto.StoreGoodsSkuReplaceDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuCrossShopPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuFacetQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuSaveDTO;
import com.panoramic.contract.store.dto.StoreGoodsSpuUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockBatchUpdateDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsStockUpdateDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsSkuSnapshotVO;
import com.panoramic.contract.store.vo.StoreGoodsStockPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuCrossShopPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuFacetVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.store.entity.StoreGoodsSpu;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 店铺在售商品（SPU）服务（store 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口承载店铺商品的
 * 上下架联动、SKU 锁定规则与平台锁定能力（见下）。</p>
 * <p><b>按能力分组，不按端分侧</b>（cross-cutting 第 22/23 条）：
 * <ul>
 *   <li><b>有作用域维度</b>（{@code storeId} 必填，无全量视角）：{@link #page} / {@link #detail}
 *       （作用域可空，见其注释）/ {@link #save} / {@link #update} / {@link #delete} /
 *       {@link #replaceSkus} / {@link #updateSkuShelf} / 库存三条——只作用于 {@code store_id == storeId}
 *       的行（R11），SKU 侧操作先校验其 SPU 归属；</li>
 *   <li><b>跨店通用</b>（无锚点，调用方自设限定条件）：{@link #crossShopPage} / {@link #facets} /
 *       {@link #details} / {@link #lock} / {@link #unlock}。</li>
 * </ul>
 * 「谁传什么作用域」全由端 BFF 决定，域内不做身份判断、不按端分流。</p>
 * <p>规则口径：
 * <ul>
 *   <li>R1 新增：SPU 与全部 SKU 一律以下架态落库；每个 SKU 价格必填且 ≥0.01；</li>
 *   <li>R2/R3 联动：上架任一 SKU → SPU 自动上架；SKU 全下架 → SPU 自动下架
 *       （不变量 {@code SPU上架 ⟺ ≥1 SKU 上架}，故 SPU 无独立上下架入口）；</li>
 *   <li>R4/R5：已上架 SKU 整行锁死（不可改、不可删），未上架 SKU 可增可改可删；</li>
 *   <li>R6：存在上架 SKU 时 {@code spec_config} 不可改；</li>
 *   <li>R8：存在上架 SKU 时拒绝删 SPU；否则软删并级联软删其下全部 SKU；</li>
 *   <li>R12 平台锁定：锁定把名下 SKU 全部级联下架、SPU 随之推导为下架（推导规则不变）；
 *       锁定期作用域内的改/删/改 SKU/上下架/改库存 一律拒绝（整行只读）；解锁是独立能力，
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
     * 商品详情（含 SKU 列表与锁定信息），回填所属店铺名。
     * <p><b>一条能力一个方法</b>（cross-cutting 第 22 条）：作用域 {@code storeId} <b>可空</b>——
     * 传了就按「id + store_id」双条件取行（店主侧只看本店，不属本店与不存在同样报「商品不存在」、
     * 不泄露存在性），没传就是不限定（管理端 / C 端跨店详情，取不到即报「商品不存在」）。</p>
     * <p>⚠ 出参是<b>管理端超集</b>（含 {@code lockUser} / {@code storeName} 等）：
     * 「域返回了」不等于「可以对外下发」，各端 BFF 输出前自行裁剪。</p>
     *
     * @param id      店铺商品 id
     * @param storeId 作用域（店主账号 id / 店铺 id）；null = 不限定店铺
     * @return 商品详情（{@code categoryPath} 由端 BFF 读时解析，域不持分类表）
     */
    StoreGoodsSpuPlatformDetailVO detail(Long id, Long storeId);

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
     * @param id      店铺商品 id
     * @param storeId 店主账号 id（作用域；与 {@link #detail(Long, Long)} 同次序，也和 Feign / controller 一致）
     */
    void delete(Long id, Long storeId);

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

    // ---- 有作用域维度：SKU 库存（库存读写落在 store_goods_sku_stock，本类的职责是归属校验与编排）----

    /**
     * SKU 库存分页（按 SKU 平铺一行一条；仅 storeId 名下）。
     * <p>筛选：{@code keyword}（商品名 / SKU 编码）、{@code shelfStatus}、{@code lowStockOnly}
     * （{@code stock <= warn_stock}）。库存列取自库存表，缺失行按 0 计。</p>
     * <p>读路径无 N+1：店铺过滤走 {@code inSql} 子查询，商品名与库存各一次批量查（R14 第 4 条）。</p>
     *
     * @param storeId 店主账号 id（== 店铺主键）
     * @param dto     分页/筛选参数
     * @return 分页结果
     */
    PageResult<StoreGoodsStockPageItemVO> pageStock(Long storeId, StoreGoodsStockPageQueryDTO dto);

    /**
     * 改单行 SKU 库存。{@code warnStock} 传 null = 清除预警。
     * <p>平台锁定期只读（{@link #pageStock} 之外的写操作同样受 R12 约束）。</p>
     *
     * @param storeId 店主账号 id
     * @param skuId   SKU id（经 {@code skuId → sku.spuId → spu.store_id} 链校验归属）
     * @param dto     库存与预警值
     */
    void updateSkuStock(Long storeId, Long skuId, StoreGoodsStockUpdateDTO dto);

    /**
     * 批量设置整批 SKU 的总库存（单条 {@code IN} 更新，不循环逐行）。
     * <p>逐个校验归属：不属于本店的 SKU 直接拒绝，不静默跳过；平台锁定期只读。</p>
     *
     * @param storeId 店主账号 id
     * @param dto     待改 SKU 集合与新总库存
     */
    void batchUpdateSkuStock(Long storeId, StoreGoodsStockBatchUpdateDTO dto);

    // ---- 跨店通用（不带 storeId 锚点：分页/聚合/批量详情为 admin BFF 与 mall-bff 共用，锁定解锁为 admin 专有）----

    /**
     * 店铺商品分页（<b>跨店通用</b>：不传 storeId 锚点，调用方自设限定条件）。
     * <p>分类与品牌均为多值匹配（{@code categoryIds} 由端 BFF 用分类树展开后传入），
     * 可再按店铺 / 店铺审核状态 / 上下架 / 锁定状态 / 名称关键字筛选，并可按 id 或最低价排序；
     * 回填店铺名与 SKU 数量。</p>
     * <p>admin BFF 用于「店铺商品管理」（不设限定条件，全量）；mall-bff 用于 C 端商品浏览
     * （固定传 {@code shopStatus=2} + {@code shelfStatus=1} + {@code lockStatus=0}）。
     * 端别差别只体现在传入条件上，域内不判身份、不做分流。</p>
     * <p>出参的 {@code categoryPath} 不在此填充（域不持分类表），由端 BFF 读时解析。</p>
     *
     * @param dto 分页/筛选/排序参数
     * @return 分页结果
     */
    PageResult<StoreGoodsSpuCrossShopPageItemVO> crossShopPage(StoreGoodsSpuCrossShopPageQueryDTO dto);

    /**
     * 商品筛选维度聚合（分类 / 品牌两个维度各有独立口径，见 {@link StoreGoodsSpuFacetVO}）
     *
     * @param dto 聚合查询参数
     * @return 两个维度的可选项及命中数
     */
    StoreGoodsSpuFacetVO facets(StoreGoodsSpuFacetQueryDTO dto);

    /**
     * 店铺商品<b>批量</b>详情（跨店，不校验归属；含 SKU 列表与锁定信息），逐条回填所属店铺名。
     * <p>调用方是 <b>mall-bff 的购物车列表</b>：一次调用取回多个 SPU，替代逐行调单条 {@link #detail}。
     * <b>SQL 条数与 {@code spuIds} 个数无关</b>（SPU / SKU / 可用库存 / 店铺名 各一次批量查询），
     * SKU 取回后按 {@code spu_id} 内存分组。</p>
     * <p>⚠ 查不到的 id（SPU 已删除）<b>跳过、不出现在出参里，不抛异常</b>。与单条版
     * {@link #detail}「取不到即报错（400）」的语义<b>刻意不同</b>：购物车行可能引用已被删除的
     * 商品，逐行报错会让整个列表取不回来，故由调用方按「拿不到 = 商品不存在」自行处理。</p>
     * <p>出参与管理端详情同形（<b>管理端超集</b>，含 {@code lockUser} / {@code goodsSpuId} 等），
     * <b>不做任何 C 端可见性裁剪</b>——那由 mall-bff 输出前完成（见 docs/contracts/store.md 第三节、
     * cross-cutting 第 17/19/20 条）。{@code categoryPath} 同样由端 BFF 读时解析。</p>
     *
     * @param spuIds 店铺商品 id 集合（null / 空集合直接返回空列表）
     * @return 商品详情列表（按查询返回顺序，不含查不到的 id；调用方按 id 索引，不依赖顺序）
     */
    List<StoreGoodsSpuPlatformDetailVO> details(List<Long> spuIds);

    /**
     * 锁定商品：写锁定状态/原因/锁定人/锁定时间，并把名下 SKU 全部级联下架，
     * 再由 {@code refreshDerived} 推导 SPU 为下架。已锁定则拒绝重复操作。
     * <p>锁定期整行只读（作用域内的写操作全数拒绝），仅本方法可解。</p>
     *
     * @param id  店铺商品 id
     * @param dto 锁定请求（原因必填）
     */
    void lock(Long id, StoreGoodsLockDTO dto);

    /**
     * 解锁商品：清空锁定字段。<b>不恢复上架</b>——SKU 保持下架，需店主手动重新上架。
     * 未锁定则拒绝。
     *
     * @param id 店铺商品 id
     */
    void unlock(Long id);

    // ---- 评价协作（跨实体只走 owner service：评价服务调这三个方法，本域不把 SPU 的 Mapper 递出去）----

    /**
     * 按商品 id 取<b>所属店铺 id</b>（提交评价时确定归属用）。
     * <p>⚠ <b>不过滤逻辑删除</b>：商品软删后，历史订单照常可以评价——这条链路上「商品还在不在售」
     * 与「能不能评价」是两回事。故本方法用显式 SQL 绕过 MP 的逻辑删除注入（普通 {@code getById}
     * 会因 {@code is_delete = 1} 查不到而误判「商品不存在」）。</p>
     *
     * @param spuId 店铺商品 id
     * @return 所属店铺 id（= 店主账号 id）
     * @throws com.panoramic.common.exception.ServiceException 该 id 无对应行时（商品不存在）
     */
    Long getStoreIdOfSpuOrThrow(Long spuId);

    /**
     * 商品 id 集合批量查商品名（评价列表回填 {@code spuName} 用，避免 N+1）。
     *
     * @param spuIds 店铺商品 id 集合
     * @return id -> 商品名；<b>查不到的 id 不出现在结果里</b>（商品已软删），调用方得 null 自行兜底展示
     */
    Map<Long, String> nameMap(Collection<Long> spuIds);

    /**
     * 回写商品评分（{@code score} 是<b>推导量</b>：该 SPU 全部评价的算术平均）。
     * <p>⚠ 本方法是该列在 SPU 侧的唯一写入口（调用方是评价服务，由它重算后传入）；
     * 商品自身的任何写路径都不得显式设置它。{@code score} 传 null = 清回「暂无评分」，
     * 故实现必须是 {@code lambdaUpdate().set(...)}（{@code updateById} 跳过 null 列，清不掉）。</p>
     *
     * @param spuId 店铺商品 id
     * @param score 平均分（保留 1 位小数）；null = 无评价
     */
    void updateScore(Long spuId, BigDecimal score);

    // ---- 交易协作（域间调用，cross-cutting 第 24 条：无作用域锚点、按资源 id 操作、域内不判身份）----

    /**
     * 按 SKU id 批量取<b>交易侧快照</b>（下单流水线落订单明细快照用，只读）。
     * <p>调用方是 <b>trade-center 的订单流水线适配器</b>：它手上只有购物车行里的 skuId 集合，
     * 故本能力<b>按资源 id 操作、无作用域锚点</b>，域内不判身份、不校验店铺归属。
     * <b>SQL 条数与 {@code skuIds} 个数无关</b>（SKU / SPU / 店铺 / 可用库存 各一次批量查询）。</p>
     * <p>⚠ <b>查不到的 skuId 跳过、不出现在出参里，不抛异常</b>（商品或 SKU 已删除）；
     * 同理 SPU 行取不到时整条跳过——不返回半个快照让调用方去猜缺的字段。
     * 与 {@link #details} 同口径，由调用方按「拿不到 = 不存在」处理。</p>
     * <p>字段口径见 {@link StoreGoodsSkuSnapshotVO}：{@code specAttrs} 是<b>已解析</b>的规格组合
     * （存储格式是 store 的实现细节，不透出库里的 JSON 串）、{@code mainImage} 空则回退 SPU 主图、
     * {@code shopStatus} 取店铺原值（<b>店铺行取不到时为 null，不补 0</b>）。</p>
     *
     * @param skuIds SKU id 集合（null / 空集合直接返回空列表）
     * @return 快照列表（不含查不到的 id；调用方按 {@code skuId} 索引，不依赖顺序）
     */
    List<StoreGoodsSkuSnapshotVO> platformSkuSnapshotBySkuIds(Collection<Long> skuIds);
}
