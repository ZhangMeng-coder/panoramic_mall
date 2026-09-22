package com.panoramic.mallbff.service;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.dto.CustomerAddressSaveDTO;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.mallbff.dto.AddressSaveDTO;
import com.panoramic.mallbff.vo.AddressStatusVO;
import com.panoramic.mallbff.vo.AddressVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * C 端收货地址编排（customer-center 域在本端的唯一落点）。
 *
 * <p><b>六条页面路径都走 {@link BffFeignCall}</b>：收货地址是顾客<b>自己的数据</b>，且列表也属「主内容」
 * 而非增强（拿不到地址簿，地址页就没有内容可渲染），故没有
 * {@code CustomerProfileBffService#loadProfile} 那种「取不到就静默留空」的读。经
 * {@code BffFeignCall} 后：域侧的业务 4xx <b>原样透传</b>，「地址不存在」（域侧 404，不区分「不存在」
 * 与「不属于该顾客」）与「地址超过 20 条」（域侧 400）都如实回页面；熔断 / 连接失败等才降级为
 * {@code 500}「地址服务暂不可用，请稍后重试」。</p>
 *
 * <p>⚠ <b>本类是「地址状态缓存」的失效点</b>（{@link AddressStatusCache}）：四个写路径
 * （新增 / 编辑 / 删除 / 设默认）<b>成功后</b>各失效一次——地址簿一变，「有没有地址 / 默认是哪条」
 * 就变了，而读路径 {@link #status} 正是拿这个派生态去省掉一次列表请求。失效失败不报错（TTL 兜底），
 * 见该类注释。</p>
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
 * <p>⚠ 域侧 {@code getAddress}（单条详情）<b>不由本类编排</b>：页面契约里没有「取单条地址」的端点
 * （列表已带全字段，编辑页用列表里的那份即可），域方法存在 ≠ 必须编排。它的调用方是
 * {@code OrderBffService}——下单要的是<b>地址快照</b>（顺带完成归属校验），取回后直接组成
 * trade-center 的下单入参，不经过本类的页面类型。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerAddressBffService {

    /** customer-center 熔断 / 连接异常降级提示 */
    private static final String ADDRESS_DOWN = "地址服务暂不可用，请稍后重试";

    /** 默认地址标记：「是」（域列 {@code is_default}：0否 / 1是） */
    private static final int IS_DEFAULT = 1;

    private final CustomerCenterClient customerCenterClient;
    private final AddressStatusCache addressStatusCache;

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
     * 我的地址状态（有没有地址 + 默认地址 id）——下单 / 改地址前那条控制流的分支依据
     *
     * <p>⚠ <b>命中缓存即回，不回下游</b>；未命中才拉一次列表派生，并把结果回填。
     * 只在**域读成功之后**才回填：读失败（customer-center 不可用 → 本方法抛出降级 500）时
     * 绝不写缓存——「读不到」不是「没有地址」，写进去会把一次故障固化成整个 TTL 的错结论。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @return 地址状态；
     * @throws com.panoramic.common.exception.ServiceException 下游业务 4xx 原样透传、故障降级 500
     */
    public AddressStatusVO status(Long customerId) {
        Optional<AddressStatusVO> cached = addressStatusCache.find(customerId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<CustomerAddressVO> addresses = BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> customerCenterClient.listAddresses(customerId));
        AddressStatusVO status = derive(addresses);
        addressStatusCache.put(customerId, status);
        return status;
    }

    /**
     * 新增地址（域侧：首条自动设为默认；最多 20 条，超限 → 400 原样透传）
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @param dto        地址字段（不含默认位）
     * @return 新地址 id
     */
    public Long save(Long customerId, AddressSaveDTO dto) {
        Long id = BffFeignCall.call("customer-center", ADDRESS_DOWN,
                () -> customerCenterClient.saveAddress(customerId, toDomain(dto)));
        // 首条新增会**自动成为默认**，故「有没有地址 / 默认是哪条」两问都可能变 → 必须失效
        addressStatusCache.evict(customerId);
        return id;
    }

    /**
     * 修改地址（域侧只改四个业务列，<b>不动默认位</b>；不存在 / 不属于该顾客 → 404 原样透传）
     *
     * <p>⚠ 即便它不动默认位也照样失效缓存：地址簿的内容变了就重派生一次，
     * 「哪几个写路径要失效」多一个例外就多一处会漏的判断，而重派生只是一次已缓存的读。</p>
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
        addressStatusCache.evict(customerId);
    }

    /**
     * 删除地址（域侧逻辑删除；⚠ 删掉默认地址后<b>不自动递补</b>，域侧口径见契约表）
     *
     * <p>⚠ 这是「有地址却无默认」那条支路的唯一来源（删掉默认且还有别的地址）——
     * 不失效缓存的话，页面会一直以为还有默认地址、拿一个已删的 id 去下单。</p>
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
        addressStatusCache.evict(customerId);
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
        addressStatusCache.evict(customerId);
    }

    /**
     * 从地址列表派生状态（**唯一一处派生**：页面那条控制流与缓存共用它）
     *
     * <p>⚠ 默认地址**按标记找**（{@code isDefault == 1}），不按「列表第一条」找：
     * 域侧确实把默认地址排在最前，但那是**列表展示的排序**，把它当成「哪条是默认」的判据
     * 等于让一个排序约定去承担一个业务事实——排序一改，这里会静默取错。</p>
     *
     * <p>⚠ 域侧保证「同一顾客至多一条默认」，故这里至多命中一条；万一命中多条（域侧不变量破了），
     * 取**最后一条**而不是第一条，只是为了让结果确定，不额外报错——本层不重判域的规则。</p>
     *
     * @param addresses 地址列表（null / 空 = 没有地址，是合法状态）
     * @return 派生的状态
     */
    private static AddressStatusVO derive(List<CustomerAddressVO> addresses) {
        AddressStatusVO status = new AddressStatusVO();
        if (addresses == null || addresses.isEmpty()) {
            status.setHasAddress(false);
            return status;
        }
        status.setHasAddress(true);
        for (CustomerAddressVO address : addresses) {
            if (address.getIsDefault() != null && address.getIsDefault() == IS_DEFAULT) {
                status.setDefaultAddressId(address.getId());
            }
        }
        return status;
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
