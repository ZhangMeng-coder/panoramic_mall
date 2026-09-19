package com.panoramic.mallbff.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 当前登录顾客信息（登录返回 &amp; /auth/me 返回；前台顶栏与资料页展示用）
 * <p>⚠ 本 VO 与 store-bff 的 CurrentUserVO <b>刻意不再同构</b>（2026-09-19 解除）：
 * mall 端含顾客资料（{@code avatar} / {@code gender} / {@code birthday}），store 端不含——
 * C 端顶栏与资料页同源，店主端无此需求。⚠ <b>不要再「照 store-bff 对齐」把这三个字段删掉。</b></p>
 * <p>填充口径的另一处差异：C 端账号即手机号，故本 VO 的 {@code phone} <b>必须填</b>
 * （值取自登录快照的 username）；store-bff 从不填 phone。</p>
 * <p>顾客账号无 RBAC 权限维度，{@code perms} 恒为空列表。</p>
 * <p>三个资料字段的<b>来源与降级</b>：取自 customer-center（经 {@code CustomerProfileBffService}），
 * 域不可用时整组留空、不阻断 {@code /auth/me}；{@code nickname} 另有「为空则回退手机号」的兜底，
 * 口径见 docs/contracts/mall-bff.md 的「资料读口径」「昵称兜底」两行。</p>
 */
@Data
public class CurrentUserVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户名（登录账号）——C 端即手机号
     */
    private String username;

    /**
     * 昵称（取自 customer-center 的顾客资料；资料为空时回退为手机号）
     */
    private String nickname;

    /**
     * 头像 URL（取自 customer-center 的顾客资料；资料不可用时留空）
     */
    private String avatar;

    /**
     * 性别：0未知，1男，2女（取自 customer-center 的顾客资料；资料不可用时留空）
     */
    private Integer gender;

    /**
     * 生日（取自 customer-center 的顾客资料；资料不可用时留空）
     */
    private LocalDate birthday;

    /**
     * 手机号（登录账号；C 端由快照回填，非查询所得）
     */
    private String phone;

    /**
     * 权限字符串集合（顾客账号固定为空）
     */
    private List<String> perms;
}
