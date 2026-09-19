package com.panoramic.customer.controller;

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
 * 不向页面暴露公网路由；方法直接返回业务原类型（不包 RespData），错误经
 * {@code CustomerDomainExceptionHandler} 以真实 HTTP 状态码传播。</p>
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
    public List<CustomerAddressVO> listAddresses(@PathVariable("customerId") Long customerId) {
        return customerAddressService.listAddresses(customerId);
    }

    /**
     * 地址详情（不存在/不归属 → 404）
     */
    @GetMapping("/{customerId}/{id}")
    public CustomerAddressVO getAddress(@PathVariable("customerId") Long customerId,
                                        @PathVariable("id") Long id) {
        return customerAddressService.getAddress(customerId, id);
    }

    /**
     * 新增地址（首条自动设为默认；最多 20 条），返回新地址 id
     */
    @PostMapping("/{customerId}")
    public Long saveAddress(@PathVariable("customerId") Long customerId,
                            @Validated @RequestBody CustomerAddressSaveDTO dto) {
        return customerAddressService.saveAddress(customerId, dto);
    }

    /**
     * 修改地址（不含默认位；设默认走 {@code /{id}/default}）
     */
    @PutMapping("/{customerId}/{id}")
    public void updateAddress(@PathVariable("customerId") Long customerId,
                              @PathVariable("id") Long id,
                              @Validated @RequestBody CustomerAddressSaveDTO dto) {
        customerAddressService.updateAddress(customerId, id, dto);
    }

    /**
     * 删除地址（删默认地址后不递补）
     */
    @DeleteMapping("/{customerId}/{id}")
    public void deleteAddress(@PathVariable("customerId") Long customerId,
                              @PathVariable("id") Long id) {
        customerAddressService.deleteAddress(customerId, id);
    }

    /**
     * 设为唯一默认地址
     */
    @PostMapping("/{customerId}/{id}/default")
    public void setDefaultAddress(@PathVariable("customerId") Long customerId,
                                  @PathVariable("id") Long id) {
        customerAddressService.setDefaultAddress(customerId, id);
    }
}
