package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.common.store.dto.ShopAuditDTO;
import com.panoramic.common.store.dto.ShopPageQueryDTO;
import com.panoramic.common.store.dto.ShopSaveDTO;
import com.panoramic.common.store.vo.PageResult;
import com.panoramic.common.store.vo.ShopVO;
import com.panoramic.store.entity.StoreShop;

/**
 * 店铺服务（store 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；新增方法承载店铺
 * 审核状态机 + store_id 数据权限适配（D5）。本切片 store 域仅一张 store_shop，且
 * 「账号店同 ID」（id==store_id==店主账号 id）；owner 操作只作用于「id==store_id 的店」，
 * platform 操作全量。方法内按 {@code X-User-Type}（admin/store）做平台/店主身份分流。</p>
 */
public interface StoreShopService extends IService<StoreShop> {

    // ---- owner（store-bff 调用，必带 store_id；X-User-Type=store）----

    /**
     * 店主「我的店铺」：以 store_id(=店主账号 id) 直查。
     *
     * @param storeId 店主账号 id（== 店铺主键）
     * @return 店铺；未开店返回 null（HTTP 200 空 body → Feign 解出 null）
     */
    ShopVO mine(Long storeId);

    /**
     * 店主保存草稿：无店则建 id=storeId 的店；已驳回(3)重新编辑回到草稿并清空审核留痕；
     * 待审核(1)/已通过(2)不允许改动（状态机守卫）
     *
     * @param storeId 店主账号 id（== 店铺主键）
     * @param dto     店铺信息
     */
    void saveDraft(Long storeId, ShopSaveDTO dto);

    /**
     * 店主提交审核：须完整填写资质字段，成功后进入待审核(1)
     *
     * @param storeId 店主账号 id（== 店铺主键）
     * @param dto     店铺信息
     */
    void submit(Long storeId, ShopSaveDTO dto);

    // ---- platform（admin 调用，不传 store_id，全量；X-User-Type=admin）----

    /**
     * 平台店铺分页列表（不读店主账号，D6）
     *
     * @param dto 分页/筛选参数
     * @return 分页结果
     */
    PageResult<ShopVO> adminPage(ShopPageQueryDTO dto);

    /**
     * 平台店铺详情（不读店主账号，D6）
     *
     * @param id 店铺ID
     * @return 店铺详情
     */
    ShopVO adminDetail(Long id);

    /**
     * 平台审核：仅对「待审核(1)」做条件更新（防重复审核/并发下越权）；通过→2，驳回→3 且驳回原因必填
     *
     * @param id  店铺ID
     * @param dto 审核参数
     */
    void adminAudit(Long id, ShopAuditDTO dto);
}
