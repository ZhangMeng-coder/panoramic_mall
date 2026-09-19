package com.panoramic.mallbff.controller;

import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.mallbff.dto.ProfileSaveDTO;
import com.panoramic.mallbff.service.CustomerProfileBffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端顾客资料接口（<b>只写</b>）。
 * <p>⚠ <b>不单开 {@code GET /profile}</b>：资料读合并在 {@code /auth/me} 的出参里
 * （见 docs/contracts/mall-bff.md 的「资料读口径」），本类只承载写。</p>
 * <p>⚠ <b>顾客 id 只能取自 {@code UserContext}</b>（登录态），绝不从请求体 / 路径接收——
 * 域内不做任何鉴权（{@code customerId} 就是数据权限本身），BFF 是唯一授权点。</p>
 * <p>分层：本类只碰 {@link CustomerProfileBffService}，<b>不注入</b> {@code CustomerCenterClient}。</p>
 * <p>⚠ <b>C 端不接 RBAC</b>：本类没有、也不应有任何 {@code @PreAuthorize}——顾客对自己的数据全权限。</p>
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CustomerProfileBffService customerProfileBffService;

    /**
     * 保存顾客资料（<b>整份替换</b>：四项全传，未传即写为 NULL；需登录态）
     * <p>写操作<b>不可降级</b>：下游故障经 {@code BffFeignCall} 降级为 500「顾客资料暂不可用」抛出
     * （由统一异常处理还原给页面），业务 4xx 原样透传。</p>
     */
    @PutMapping
    public RespData<Void> save(@Valid @RequestBody ProfileSaveDTO dto) {
        customerProfileBffService.saveProfile(UserContext.getUserId(), toSaveDTO(dto));
        return RespData.success();
    }

    /**
     * 页面 DTO → 域 DTO：<b>四个字段逐个全量映射</b>，不做任何 null 过滤。
     * <p>⚠ 一个都不能漏、也不许写成「非 null 才 set」：域侧写的是整份覆盖，
     * 漏映射一个字段等于静默把它清空（见 docs/contracts/mall-bff.md 的「资料写口径」）。</p>
     */
    private CustomerProfileSaveDTO toSaveDTO(ProfileSaveDTO dto) {
        CustomerProfileSaveDTO target = new CustomerProfileSaveDTO();
        target.setNickname(dto.getNickname());
        target.setAvatar(dto.getAvatar());
        target.setGender(dto.getGender());
        target.setBirthday(dto.getBirthday());
        return target;
    }
}
