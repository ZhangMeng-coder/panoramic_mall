package com.panoramic.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.customer.entity.CustomerProfile;

/**
 * 顾客资料服务（customer-center 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口只承载资料的两个领域入口。
 * 数据权限锚点 {@code customerId = mall_user.id}：两个方法**全按传入锚点过滤**，锚点即数据权限本身。
 * <b>本域不做任何鉴权/身份判断</b>——调用方传的 customerId 是否「本人」由 mall-bff 从登录态取，
 * 域侧不校验（防线在 BFF）。</p>
 */
public interface CustomerProfileService extends IService<CustomerProfile> {

    /**
     * 读顾客资料：无记录返回空 VO（id 填 customerId、其余字段 null），**不返回 null、不抛 404**，
     * 调用方不必判空。
     *
     * @param customerId 顾客账号 id（== 资料主键）
     * @return 资料视图对象；未建资料行时仅有 id
     */
    CustomerProfileVO getProfile(Long customerId);

    /**
     * 保存顾客资料（惰性创建）：无记录则 INSERT（显式写 id=customerId），有则 UPDATE。
     *
     * @param customerId 顾客账号 id（== 资料主键）
     * @param dto        资料字段（全字段选填）
     */
    void saveProfile(Long customerId, CustomerProfileSaveDTO dto);
}
