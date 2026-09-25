package com.panoramic.mallbff.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * C 端顾客资料保存请求参数（页面级，{@code PUT /profile} 的入参）。
 *
 * <p>⚠ <b>写口径是「整份替换」而不是「增量更新」</b>：四个字段全部选填（不带
 * {@code @NotNull} / {@code @NotBlank}），但未传的字段会被<b>写成 NULL</b>——
 * 域侧 {@code saveProfile} 对四列无条件写入，本端也<b>不做 null 过滤</b>（两边语义必须同向）。
 * 要「只改一个字段」，得先读 {@code /auth/me} 拿到完整资料再整体回传。
 * 这是刻意的：入口是 PUT（PUT 语义即整份替换），前端资料页是「昵称 / 头像 / 性别 / 生日」
 * 四项的整表单、每次都全量提交。口径见 docs/contracts/mall-bff.md 的「资料写口径」。</p>
 *
 * <p>约束<b>与域侧 {@code CustomerProfileSaveDTO} 镜像</b>（长度 / 取值范围逐条对齐）：
 * BFF 是页面边界，坏输入该在<b>这里</b>回 400，而不是穿到域里再经熔断语义绕一圈绕回来。</p>
 */
@Data
public class ProfileSaveDTO {

    /**
     * 昵称
     * <p>⚠ <b>留空不会写成 NULL</b>：本层用默认昵称规则「用户」+ 手机号后 4 位补齐后再转发
     * （{@code CustomerProfileBffService#withDefaultNickname}，规则本体的唯一实现处，见契约的
     * 「默认昵称」）——否则顾客「清空昵称」会重新制造无昵称用户。其余三项照旧整份覆盖。</p>
     */
    @Size(max = 50, message = "昵称不能超过50个字符")
    private String nickname;

    /**
     * 头像 URL
     */
    @Size(max = 255, message = "头像地址长度不能超过255个字符")
    private String avatar;

    /**
     * 性别：0未知，1男，2女
     * <p>⚠ 取值范围必须在此收口：超范围的枚举值（如 9）会被静默入库、前端拿不到任何已知分支；
     * 更大的越界值则触发 MySQL 严格模式报错，经域兜底变成 HTTP 500——而 5xx 会计入端 BFF 的
     * 熔断失败率（见 docs/contracts/cross-cutting.md 第 13 条）。</p>
     */
    @Min(value = 0, message = "性别取值不正确")
    @Max(value = 2, message = "性别取值不正确")
    private Integer gender;

    /**
     * 生日
     */
    private LocalDate birthday;
}
