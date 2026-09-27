package com.panoramic.customer.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.customer.dto.CustomerProfileBatchQueryDTO;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.dto.CustomerStatsQueryDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.contract.customer.vo.CustomerStatsVO;
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
 * 不向页面暴露公网路由。出参一律包 {@code RespData<T>}（cross-cutting 第 2 条）：业务结果（含业务失败）
 * 走 HTTP 200 + {@code code}，异常由 common 的 {@code GlobalExceptionHandler} 兜底成 HTTP 500 —— 那是熔断
 * 唯一的失败信号（第 13 条）。</p>
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：路径上的 {@code customerId} 就是数据权限锚点，
 * 它是否等于「本人」由 mall-bff 从登录态取，域侧不校验（防线在 BFF）。身份头只由
 * {@code CustomerUserIdentityFilter} 填 {@code UserContext} 供审计留痕，缺头即留空、不回 401。</p>
 * <p>⚠ {@link #getCustomerStats} 的调用方是 <b>admin BFF</b>（平台首页看板）而非 mall-bff，且<b>无锚点</b>：
 * 上一条「路径上的 customerId 就是数据权限锚点」只适用于其余三个方法。</p>
 */
@RestController
@RequestMapping("/internal/customer/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CustomerProfileService customerProfileService;

    /**
     * 读顾客资料：无资料行返回「仅含 id 的空 VO」（{@code code=200, data=空VO}），不返回 null、不抛 404
     */
    @GetMapping("/{customerId}")
    public RespData<CustomerProfileVO> getProfile(@PathVariable("customerId") Long customerId) {
        return RespData.success(customerProfileService.getProfile(customerId));
    }

    /**
     * 保存顾客资料（惰性建行：无记录则建 id=customerId 的资料行）
     */
    @PostMapping("/{customerId}")
    public RespData<Void> saveProfile(@PathVariable("customerId") Long customerId,
                                     @Validated @RequestBody CustomerProfileSaveDTO dto) {
        customerProfileService.saveProfile(customerId, dto);
        return RespData.success();
    }

    /**
     * <b>批量</b>读顾客资料（评价列表一次补齐昵称 / 头像，消除 N+1）。
     * <p>⚠ 与上一条单读的路径形状刻意不同：它是<b>一次查一批</b>、不是「查某一个」，
     * 故走 {@code /batch} 而不是复用 {@code /{customerId}}（后者会把「一批 id」塞进一个路径变量）。
     * 与 {@code @PostMapping("/{customerId}")} 不冲突：Spring 的路径匹配里字面量段优先于变量段。</p>
     * <p>⚠ 查不到的 id <b>跳过</b>，不补空 VO（与单读 {@link #getProfile} 的有意分歧，见契约页）。</p>
     */
    @PostMapping("/batch")
    public RespData<List<CustomerProfileVO>> listProfilesByIds(@Validated @RequestBody CustomerProfileBatchQueryDTO dto) {
        return RespData.success(customerProfileService.listProfilesByIds(dto.getCustomerIds()));
    }

    /**
     * <b>顾客统计</b>（平台首页数据看板）：累计资料数 + 窗口内每日新增。调用方是 admin BFF。
     * <p>⚠ 与 {@code @GetMapping("/{customerId}")} 同段不冲突：Spring 的<b>字面量模式优先于模板模式</b>，
     * {@code /profile/stats} 落到本方法、不会去匹配那个 {@code Long} 变量（否则会因类型转换失败回 400）。
     * 与 {@code /profile/batch} 之于 {@code @PostMapping("/{customerId}")} 是同一种共存，不是新引入的风险。</p>
     * <p>⚠ 本方法<b>无锚点、不按顾客过滤</b>——它数全平台（域内仍不鉴权，「谁能看全平台」由 admin 侧决定）。</p>
     * <p>⚠ 只收显式起止，<b>没有窗口枚举、没有粒度</b>：月/季/年与日→月归并只在 admin BFF
     * （cross-cutting 第 25 条）。两个起止都可省（省了只回快照），只给一端 → 400。</p>
     */
    @GetMapping("/stats")
    public RespData<CustomerStatsVO> getCustomerStats(@Validated CustomerStatsQueryDTO query) {
        return RespData.success(customerProfileService.getCustomerStats(query));
    }
}
