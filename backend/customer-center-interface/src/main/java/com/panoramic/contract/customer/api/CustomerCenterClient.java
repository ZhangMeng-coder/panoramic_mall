package com.panoramic.contract.customer.api;

import com.panoramic.contract.customer.dto.CustomerAddressSaveDTO;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * customer-center（顾客域，下沉纯域）内部 Feign 客户端。
 * <p>顾客域持「顾客资料 {@code customer_profile} + 收货地址 {@code customer_address}」，不向页面暴露公网路由，
 * 只被 mall-bff 经本接口内部调用。规约（见 docs/contracts/customer-center.md 与 cross-cutting.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 customer-center-interface（域服务端、mall-bff 客户端引用同一份类型）；</li>
 *   <li>方法直接返回业务结果类型（不包 RespData），错误走异常统一传播；</li>
 *   <li>调用经 {@link CustomerFeignConfiguration} 附带信任头 + 透传主身份 + 错误解码；熔断由<b>调用方</b>经 Nacos
 *       {@code feign-circuitbreaker.yml} 配置提供，不在本类。</li>
 * </ul>
 * 数据权限口径：锚点 {@code customerId} = {@code mall_user.id}（跨域 id 引用、无外键），所有方法全按传入锚点过滤；
 * <b>无 owner / platform 分侧</b>——本期只做 C 端自助，调用方传的 {@code customerId} 是否「本人」由 mall-bff
 * 从登录态取，域内不做任何身份判断（不读 X-User-Type 判权；该头只用于审计留痕）。
 * 服务端路径与映射需与 customer-center 域内部控制器一一对应（前缀 /internal/customer）。</p>
 * <p>⚠ 方法<b>按行分批补齐</b>：摘掉契约表（docs/contracts/customer-center.md）某行的 {@code 待实现} 标记、
 * 在此声明该方法、域侧补上实现，三者必须落在同一个提交里（否则 drift-check 的标记腐烂反向哨兵会报错）。
 * 当前已补齐「顾客资料」两条（getProfile / saveProfile）与「收货地址」六条（list/get/save/update/delete/setDefault），
 * 契约表 8 条**全部落地**（{@code 待实现} 归零）。</p>
 */
@FeignClient(name = "customer-center", contextId = "customerCenterClient",
        path = "/internal/customer", configuration = CustomerFeignConfiguration.class)
public interface CustomerCenterClient {

    /**
     * 读顾客资料：无资料行返回「仅含 id 的空 VO」，不返回 null、不抛 404（调用方不必判空）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 顾客资料
     */
    @GetMapping("/profile/{customerId}")
    CustomerProfileVO getProfile(@PathVariable("customerId") Long customerId);

    /**
     * 保存顾客资料（域侧惰性建行：无记录则建 id=customerId 的资料行）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        资料字段
     */
    @PostMapping("/profile/{customerId}")
    void saveProfile(@PathVariable("customerId") Long customerId, @RequestBody CustomerProfileSaveDTO dto);

    /**
     * 我的收货地址列表（默认地址排最前）；无地址返回空列表，不返回 null
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 地址列表
     */
    @GetMapping("/addresses/{customerId}")
    List<CustomerAddressVO> listAddresses(@PathVariable("customerId") Long customerId);

    /**
     * 地址详情（不存在或不属于该顾客 → 404「地址不存在」，不区分两种情形）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param id         地址 id
     * @return 地址视图对象
     */
    @GetMapping("/addresses/{customerId}/{id}")
    CustomerAddressVO getAddress(@PathVariable("customerId") Long customerId,
                                 @PathVariable("id") Long id);

    /**
     * 新增地址（首条自动设为默认；最多 20 条，超限 → 400）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        地址字段（**不含 isDefault**：设默认只能走 setDefaultAddress）
     * @return 新地址 id
     */
    @PostMapping("/addresses/{customerId}")
    Long saveAddress(@PathVariable("customerId") Long customerId,
                     @RequestBody CustomerAddressSaveDTO dto);

    /**
     * 修改地址（只改四个业务列，**不动默认位**）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param id         地址 id
     * @param dto        地址字段
     */
    @PutMapping("/addresses/{customerId}/{id}")
    void updateAddress(@PathVariable("customerId") Long customerId,
                       @PathVariable("id") Long id,
                       @RequestBody CustomerAddressSaveDTO dto);

    /**
     * 删除地址（逻辑删除）；⚠ 删掉默认地址后**不自动递补**
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param id         地址 id
     */
    @DeleteMapping("/addresses/{customerId}/{id}")
    void deleteAddress(@PathVariable("customerId") Long customerId,
                       @PathVariable("id") Long id);

    /**
     * 设为唯一默认地址（同事务内先清该顾客的默认位、再置目标行；并发由该顾客名下行锁串行化）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param id         地址 id
     */
    @PostMapping("/addresses/{customerId}/{id}/default")
    void setDefaultAddress(@PathVariable("customerId") Long customerId,
                           @PathVariable("id") Long id);
}
