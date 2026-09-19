package com.panoramic.contract.customer.api;

import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * customer-center（顾客域，下沉纯域）内部 Feign 客户端。
 * <p>顾客域持「顾客资料 {@code customer_profile} + 收货地址 {@code customer_address}」，不向页面暴露公网路由，
 * 只被 mall-bff 经本接口内部调用。规约（见 CLAUDE.md 与 docs/contracts/customer-center.md）：
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
 * 当前已补齐「顾客资料」两条（getProfile / saveProfile），地址 6 条仍标 {@code 待实现}。</p>
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
}
