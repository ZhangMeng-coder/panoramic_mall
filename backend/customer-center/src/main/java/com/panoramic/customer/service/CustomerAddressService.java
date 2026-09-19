package com.panoramic.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.customer.dto.CustomerAddressSaveDTO;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.customer.entity.CustomerAddress;

import java.util.List;

/**
 * 收货地址服务（customer-center 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口只承载地址的六个领域入口。
 * 数据权限锚点 {@code customerId = mall_user.id}：六个方法**全按传入锚点过滤**，锚点即数据权限本身；
 * 不属于该顾客的地址一律按「不存在」处理（404，不泄露存在性）。
 * <b>本域不做任何鉴权/身份判断</b>——调用方传的 customerId 是否「本人」由 mall-bff 从登录态取，
 * 域侧不校验（防线在 BFF）。</p>
 */
public interface CustomerAddressService extends IService<CustomerAddress> {

    /**
     * 列出该顾客的全部收货地址，**默认地址排最前**（同默认时按 id 倒序，新地址在前）。
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 地址列表；无地址返回空列表（不返回 null、不抛 404）
     */
    List<CustomerAddressVO> listAddresses(Long customerId);

    /**
     * 读单条地址详情。
     *
     * @param customerId 顾客账号 id（数据权限锚点）
     * @param id         地址 id
     * @return 地址视图对象
     * @throws com.panoramic.common.exception.ServiceException 地址不存在或不属于该顾客（HTTP 404）
     */
    CustomerAddressVO getAddress(Long customerId, Long id);

    /**
     * 新增地址；**首条自动设为默认**（不递补、不由调用方指定，故 DTO 不含 isDefault）。
     *
     * @param customerId 顾客账号 id（数据权限锚点）
     * @param dto        地址字段
     * @return 新地址 id
     * @throws com.panoramic.common.exception.ServiceException 已达 20 条上限（HTTP 400）
     */
    Long saveAddress(Long customerId, CustomerAddressSaveDTO dto);

    /**
     * 修改地址的四个业务列（**不含 isDefault**：设默认只能走 {@link #setDefaultAddress}）。
     *
     * @param customerId 顾客账号 id（数据权限锚点）
     * @param id         地址 id
     * @param dto        地址字段
     * @throws com.panoramic.common.exception.ServiceException 地址不存在或不属于该顾客（HTTP 404）
     */
    void updateAddress(Long customerId, Long id, CustomerAddressSaveDTO dto);

    /**
     * 删除地址（逻辑删除）。⚠ **删掉默认地址后不自动递补**，其余地址保持原状态。
     *
     * @param customerId 顾客账号 id（数据权限锚点）
     * @param id         地址 id
     * @throws com.panoramic.common.exception.ServiceException 地址不存在或不属于该顾客（HTTP 404）
     */
    void deleteAddress(Long customerId, Long id);

    /**
     * 把指定地址设为唯一默认地址（同事务内先清该顾客所有行的默认位、再置目标行）。
     *
     * @param customerId 顾客账号 id（数据权限锚点）
     * @param id         地址 id
     * @throws com.panoramic.common.exception.ServiceException 地址不存在或不属于该顾客（HTTP 404）
     */
    void setDefaultAddress(Long customerId, Long id);
}
