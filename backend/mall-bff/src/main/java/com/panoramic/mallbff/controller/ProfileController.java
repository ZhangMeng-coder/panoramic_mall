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
        // ⚠ 账号即手机号：手机号就是登录态快照的 username（本端不另查库，同 AuthService#toCurrentUser）
        String phone = UserContext.getLoginUser() == null ? null : UserContext.getLoginUser().getUsername();
        customerProfileBffService.saveProfile(UserContext.getUserId(), toSaveDTO(dto, phone));
        return RespData.success();
    }

    /**
     * 页面 DTO → 域 DTO：<b>四个字段逐个全量映射</b>，不做任何 null 过滤。
     * <p>⚠ 一个都不能漏、也不许写成「非 null 才 set」：域侧写的是整份覆盖，
     * 漏映射一个字段等于静默把它清空（见 docs/contracts/mall-bff.md 的「资料写口径」）。</p>
     * <p>⚠ <b>昵称是唯一的例外</b>：留空时用默认昵称规则「用户」+ 手机号后 4 位补齐
     * （{@link CustomerProfileBffService#withDefaultNickname}，规则本体的唯一实现处）——
     * 放它写成 NULL，顾客「清空昵称」就又造出一个无昵称用户，评价区只能显示占位名。</p>
     *
     * @param dto   页面资料
     * @param phone 手机号（= 登录账号，取自登录态快照；仅默认昵称规则用）
     * @return 域资料写参
     */
    private CustomerProfileSaveDTO toSaveDTO(ProfileSaveDTO dto, String phone) {
        CustomerProfileSaveDTO target = new CustomerProfileSaveDTO();
        target.setNickname(CustomerProfileBffService.withDefaultNickname(dto.getNickname(), phone));
        target.setAvatar(dto.getAvatar());
        target.setGender(dto.getGender());
        target.setBirthday(dto.getBirthday());
        return target;
    }
}
