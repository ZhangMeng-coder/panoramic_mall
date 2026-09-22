package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.store.dto.ShopAuditDTO;
import com.panoramic.contract.store.dto.ShopPageQueryDTO;
import com.panoramic.contract.store.dto.ShopSaveDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.ShopOptionVO;
import com.panoramic.contract.store.vo.ShopVO;
import com.panoramic.store.entity.StoreShop;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 店铺服务（store 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；新增方法承载店铺
 * 审核状态机。本切片 store 域仅一张 store_shop，且「账号店同 ID」（id==store_id==店主账号 id）。</p>
 * <p><b>接口按能力通用、不按端分侧</b>（cross-cutting 第 22 条）：店铺详情<b>一条路径一个方法</b>
 * （{@link #getShop}）服务所有调用方——店主侧查的就是自己账号 id 那份、管理端跨店查的也是同一行；
 * 写方法的作用域来自入参 DTO 的 {@code storeId}。域内不做身份判断（不读 X-User-Type 判权；
 * 该头只用于审计留痕）。</p>
 */
public interface StoreShopService extends IService<StoreShop> {

    /**
     * 店铺详情（跨调用方通用：店主侧传自己的 {@code storeId}、管理端传任意店铺 id）。
     * <p>⚠ <b>查不到返空、不抛</b>：「未开店」「店铺不可见」都是正常态而非故障，
     * 由各端 BFF 按自己的话术重判。</p>
     *
     * @param id 店铺 id（== 店主账号 id）
     * @return 店铺；不存在返回 null（HTTP 200 空 body → Feign 解出 null）
     */
    ShopVO getShop(Long id);

    /**
     * 保存草稿：无店则建 id=storeId 的店；已驳回(3)重新编辑回到草稿并清空审核留痕；
     * 待审核(1)/已通过(2)不允许改动（状态机守卫）
     *
     * @param storeId 店主账号 id（== 店铺主键，取自入参 DTO）
     * @param dto     店铺信息
     */
    void saveDraft(Long storeId, ShopSaveDTO dto);

    /**
     * 提交审核：须完整填写资质字段，成功后进入待审核(1)
     *
     * @param storeId 店主账号 id（== 店铺主键，取自入参 DTO）
     * @param dto     店铺信息
     */
    void submit(Long storeId, ShopSaveDTO dto);

    /**
     * 店铺分页列表（全量——本能力存在合法全量视角，故无作用域维度，D6）
     *
     * @param dto 分页/筛选参数
     * @return 分页结果
     */
    PageResult<ShopVO> adminPage(ShopPageQueryDTO dto);

    /**
     * 审核店铺：仅对「待审核(1)」做条件更新（防重复审核/并发下越权）；通过→2，驳回→3 且驳回原因必填
     *
     * @param id  店铺ID
     * @param dto 审核参数
     */
    void adminAudit(Long id, ShopAuditDTO dto);

    /**
     * 店铺 ID 集合批量查店铺名（店铺商品列表回填 storeName 用，避免 N+1）
     *
     * @param ids 店铺 id 集合
     * @return id -> 店铺名；查不到的 id 不出现在结果里（调用方回退空串）
     */
    Map<Long, String> nameMap(Collection<Long> ids);

    /**
     * 按审核状态取店铺 id 集合（跨店商品查询按店铺状态过滤用，避免 join）
     *
     * @param status 审核状态（0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回）
     * @return 店铺 id 列表；无则空列表
     */
    List<Long> idListByStatus(Integer status);

    /**
     * 店铺下拉选项（管理后台「店铺商品管理」按店铺筛选用），按 id 升序。
     * <p>不按审核状态过滤：未审核通过的店铺本就没有商品，过滤无收益。</p>
     *
     * @return 店铺 id + 名称列表
     */
    List<ShopOptionVO> options();
}
