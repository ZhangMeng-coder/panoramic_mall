package com.panoramic.mallbff.service;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.dto.CustomerAddressSaveDTO;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.mallbff.dto.AddressSaveDTO;
import com.panoramic.mallbff.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * C 端收货地址编排（customer-center 域在本端的唯一落点）。
 *
 * <p><b>五条路径都走 {@link BffFeignCall}</b>：收货地址是顾客<b>自己的数据</b>，且列表也属「主内容」
 * 而非增强（拿不到地址簿，地址页就没有内容可渲染），故没有
 * {@code CustomerProfileBffService#loadProfile} 那种「取不到就静默留空」的读。经
 * {@code BffFeignCall} 后：域侧的业务 4xx <b>原样透传</b>，「地址不存在」（域侧 404，不区分「不存在」
 * 与「不属于该顾客」）与「地址超过 20 条」（域侧 400）都如实回页面；熔断 / 连接失败等才降级为
 * {@code 500}「地址服务暂不可用，请稍后重试」。</p>
 *
 * <p>⚠ <b>{@code customerId} 一律取自登录态</b>（调用方传 {@code UserContext.getUserId()}，本类不碰登录态），
 * 绝不从请求路径 / 请求体接收——{@code customerId} 就是数据权限锚点本身，而域内不做任何鉴权，
 * BFF 是唯一授权点。</p>
 *
 * <p>⚠ <b>页面类型 ↔ 域契约类型的映射收在本类</b>：{@code AddressVO} / {@code AddressSaveDTO} 是本端私有类型
 * （域契约只许在 BFF 与域之间流动、页面类型不外扩到域），两个方向的转换各自只有一处，
 * 故 controller 只碰本端类型与 {@code CustomerAddressBffService}，不引
 * {@code com.panoramic.contract.customer} 包。映射<b>逐字段手工写</b>、不用
 * {@code BeanUtils.copyProperties}（域 VO 日后加字段不会自动漏到页面）。</p>
 *
 * <p>⚠ 域侧 {@code getAddress}（单条详情）<b>本期不编排</b>：页面契约里没有「取单条地址」的端点
 * （列表已带全字段，编辑页用列表里的那份即可），域方法存在 ≠ 必须编排。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAddressBffService {

    /** customer-center 熔断 / 连接异常降级提示 */
    private static final String ADDRESS_DOWN = "地址服务暂不可用，请稍后重试";

    private final CustomerCenterClient customerCenterClient;

    /**
     * 我的收货地址列表（默认地址排最前）
     * <p>域侧无地址返回空列表、不返回 null（故此处不对列表做判空兜底）。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @return 地址列表（页面类型）
     */
    public List<AddressVO> list(Long customerId) {
        return BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> customerCenterClient.listAddresses(customerId).stream().map(this::toVO).toList());
    }

    /**
     * 新增地址（域侧：首条自动设为默认；最多 20 条，超限 → 400 原样透传）
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @param dto        地址字段（不含默认位）
     * @return 新地址 id
     */
    public Long save(Long customerId, AddressSaveDTO dto) {
        return BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> customerCenterClient.saveAddress(customerId, toDomain(dto)));
    }

    /**
     * 修改地址（域侧只改四个业务列，<b>不动默认位</b>；不存在 / 不属于该顾客 → 404 原样透传）
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @param id         地址 id
     * @param dto        地址字段
     */
    public void update(Long customerId, Long id, AddressSaveDTO dto) {
        BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> {
                    customerCenterClient.updateAddress(customerId, id, toDomain(dto));
                    return null;
                });
    }

    /**
     * 删除地址（域侧逻辑删除；⚠ 删掉默认地址后<b>不自动递补</b>，域侧口径见契约表）
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @param id         地址 id
     */
    public void delete(Long customerId, Long id) {
        BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> {
                    customerCenterClient.deleteAddress(customerId, id);
                    return null;
                });
    }

    /**
     * 设为唯一默认地址（域侧同事务清位 + 置位）
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @param id         地址 id
     */
    public void setDefault(Long customerId, Long id) {
        BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> {
                    customerCenterClient.setDefaultAddress(customerId, id);
                    return null;
                });
    }

    /**
     * 页面 DTO → 域 DTO：四个字段逐个映射（域侧 {@code CustomerAddressSaveDTO} 不含默认位）
     */
    private CustomerAddressSaveDTO toDomain(AddressSaveDTO dto) {
        CustomerAddressSaveDTO target = new CustomerAddressSaveDTO();
        target.setReceiverName(dto.getReceiverName());
        target.setReceiverPhone(dto.getReceiverPhone());
        target.setRegion(dto.getRegion());
        target.setDetailAddress(dto.getDetailAddress());
        return target;
    }

    /**
     * 域 VO → 页面 VO：六个字段逐个映射（不用 {@code BeanUtils}，域 VO 加字段不会自动漏到页面）
     */
    private AddressVO toVO(CustomerAddressVO source) {
        AddressVO target = new AddressVO();
        target.setId(source.getId());
        target.setReceiverName(source.getReceiverName());
        target.setReceiverPhone(source.getReceiverPhone());
        target.setRegion(source.getRegion());
        target.setDetailAddress(source.getDetailAddress());
        target.setIsDefault(source.getIsDefault());
        return target;
    }
}
