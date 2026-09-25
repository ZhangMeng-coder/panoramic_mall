package com.panoramic.customer.controller;

import com.panoramic.contract.customer.dto.CustomerProfileBatchQueryDTO;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.customer.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 顾客资料内部领域接口（customer-center 域下沉纯域）。
 * <p>仅供端 BFF（mall-bff，C 端顾客自助）经内部 Feign（{@code /internal/customer/profile/...}）调用，
 * 不向页面暴露公网路由；方法直接返回业务原类型（不包 RespData），错误经
 * {@code CustomerDomainExceptionHandler} 以真实 HTTP 状态码传播。</p>
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：路径上的 {@code customerId} 就是数据权限锚点，
 * 它是否等于「本人」由 mall-bff 从登录态取，域侧不校验（防线在 BFF）。身份头只由
 * {@code CustomerUserIdentityFilter} 填 {@code UserContext} 供审计留痕，缺头即留空、不回 401。</p>
 */
@RestController
@RequestMapping("/internal/customer/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CustomerProfileService customerProfileService;

    /**
     * 读顾客资料：无资料行返回「仅含 id 的空 VO」（HTTP 200），不返回 null、不抛 404
     */
    @GetMapping("/{customerId}")
    public CustomerProfileVO getProfile(@PathVariable("customerId") Long customerId) {
        return customerProfileService.getProfile(customerId);
    }

    /**
     * 保存顾客资料（惰性建行：无记录则建 id=customerId 的资料行）
     */
    @PostMapping("/{customerId}")
    public void saveProfile(@PathVariable("customerId") Long customerId,
                            @Validated @RequestBody CustomerProfileSaveDTO dto) {
        customerProfileService.saveProfile(customerId, dto);
    }

    /**
     * <b>批量</b>读顾客资料（评价列表一次补齐昵称 / 头像，消除 N+1）。
     * <p>⚠ 与上一条单读的路径形状刻意不同：它是<b>一次查一批</b>、不是「查某一个」，
     * 故走 {@code /batch} 而不是复用 {@code /{customerId}}（后者会把「一批 id」塞进一个路径变量）。
     * 与 {@code @PostMapping("/{customerId}")} 不冲突：Spring 的路径匹配里字面量段优先于变量段。</p>
     * <p>⚠ 查不到的 id <b>跳过</b>，不补空 VO（与单读 {@link #getProfile} 的有意分歧，见契约页）。</p>
     */
    @PostMapping("/batch")
    public List<CustomerProfileVO> listProfilesByIds(@Validated @RequestBody CustomerProfileBatchQueryDTO dto) {
        return customerProfileService.listProfilesByIds(dto.getCustomerIds());
    }
}
