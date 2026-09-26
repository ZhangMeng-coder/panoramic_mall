package com.panoramic.customer.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.customer.dto.CustomerAddressSaveDTO;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.customer.service.CustomerAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客收货地址内部领域接口（customer-center 域下沉纯域）。
 * <p>仅供端 BFF（mall-bff，C 端顾客自助）经内部 Feign（{@code /internal/customer/addresses/...}）调用，
 * 不向页面暴露公网路由。出参一律包 {@code RespData<T>}（cross-cutting 第 2 条）：业务结果（含业务失败，
 * 如「地址不存在」）走 HTTP 200 + {@code code}，异常由 common 的 {@code GlobalExceptionHandler} 兜底成
 * HTTP 500 —— 那是熔断唯一的失败信号（第 13 条）。</p>
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：路径上的 {@code customerId} 就是数据权限锚点，
 * 它是否等于「本人」由 mall-bff 从登录态取，域侧不校验（防线在 BFF）。身份头只由
 * {@code CustomerUserIdentityFilter} 填 {@code UserContext} 供审计留痕，缺头即留空、不回 401。</p>
 */
@RestController
@RequestMapping("/internal/customer/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final CustomerAddressService customerAddressService;

    /**
     * 我的收货地址列表（默认地址排最前）；无地址返回空列表
     */
    @GetMapping("/{customerId}")
    public RespData<List<CustomerAddressVO>> listAddresses(@PathVariable("customerId") Long customerId) {
        return RespData.success(customerAddressService.listAddresses(customerId));
    }

    /**
     * 地址详情（不存在/不归属 → {@code code=404}「地址不存在」，不区分两种情形）
     */
    @GetMapping("/{customerId}/{id}")
    public RespData<CustomerAddressVO> getAddress(@PathVariable("customerId") Long customerId,
                                                 @PathVariable("id") Long id) {
        return RespData.success(customerAddressService.getAddress(customerId, id));
    }

    /**
     * 新增地址（首条自动设为默认；最多 20 条），返回新地址 id
     */
    @PostMapping("/{customerId}")
    public RespData<Long> saveAddress(@PathVariable("customerId") Long customerId,
                                     @Validated @RequestBody CustomerAddressSaveDTO dto) {
        return RespData.success(customerAddressService.saveAddress(customerId, dto));
    }

    /**
     * 修改地址（不含默认位；设默认走 {@code /{id}/default}）
     */
    @PutMapping("/{customerId}/{id}")
    public RespData<Void> updateAddress(@PathVariable("customerId") Long customerId,
                                       @PathVariable("id") Long id,
                                       @Validated @RequestBody CustomerAddressSaveDTO dto) {
        customerAddressService.updateAddress(customerId, id, dto);
        return RespData.success();
    }

    /**
     * 删除地址（删默认地址后不递补）
     */
    @DeleteMapping("/{customerId}/{id}")
    public RespData<Void> deleteAddress(@PathVariable("customerId") Long customerId,
                                       @PathVariable("id") Long id) {
        customerAddressService.deleteAddress(customerId, id);
        return RespData.success();
    }

    /**
     * 设为唯一默认地址
     */
    @PostMapping("/{customerId}/{id}/default")
    public RespData<Void> setDefaultAddress(@PathVariable("customerId") Long customerId,
                                           @PathVariable("id") Long id) {
        customerAddressService.setDefaultAddress(customerId, id);
        return RespData.success();
    }
}
