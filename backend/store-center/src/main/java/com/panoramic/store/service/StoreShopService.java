package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.store.dto.ShopAuditDTO;
import com.panoramic.store.dto.ShopPageQueryDTO;
import com.panoramic.store.dto.ShopSaveDTO;
import com.panoramic.store.entity.StoreShop;
import com.panoramic.store.vo.PageResult;
import com.panoramic.store.vo.ShopVO;

/**
 * 店铺服务
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；新增方法承载店铺
 * 审核状态机与店主归属校验等业务规则。跨实体（店主账号）经 {@link com.panoramic.store.service.StoreUserService} 访问。</p>
 */
public interface StoreShopService extends IService<StoreShop> {

    /**
     * 店主「我的店铺」（按当前店主归属取单条）
     *
     * @return 店铺；未创建返回 null
     */
    ShopVO mine();

    /**
     * 店主保存草稿：无店铺则新建；已驳回(3)重新编辑回到草稿并清空审核留痕；
     * 待审核(1)/已通过(2)不允许改动（状态机守卫）
     *
     * @param dto 店铺信息
     */
    void saveDraft(ShopSaveDTO dto);

    /**
     * 店主提交审核：须完整填写资质字段，成功后进入待审核(1)
     *
     * @param dto 店铺信息
     */
    void submit(ShopSaveDTO dto);

    /**
     * 平台店铺分页列表（回填店主登录账号）
     *
     * @param dto 分页/筛选参数
     * @return 分页结果
     */
    PageResult<ShopVO> adminPage(ShopPageQueryDTO dto);

    /**
     * 平台店铺详情（回填店主登录账号）
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
