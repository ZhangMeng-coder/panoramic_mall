package com.panoramic.mallbff.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前登录顾客信息（登录返回 &amp; /auth/me 返回；前台顶栏展示用）
 * <p>⚠ 与 store-bff 的 CurrentUserVO 同构（字段一致），但**填充口径不同**：C 端账号即手机号，
 * 故本 VO 的 {@code phone} **必须填**（值取自登录快照的 username）；store-bff 从不填 phone。
 * 将来若有人「照 store-bff 修」，不要把这个赋值删掉。</p>
 * <p>顾客账号无 RBAC 权限维度，{@code perms} 恒为空列表。</p>
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
     * 昵称
     */
    private String nickname;

    /**
     * 手机号（登录账号；C 端由快照回填，非查询所得）
     */
    private String phone;

    /**
     * 权限字符串集合（顾客账号固定为空）
     */
    private List<String> perms;
}
