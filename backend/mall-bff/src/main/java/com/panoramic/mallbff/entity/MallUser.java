package com.panoramic.mallbff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * C 端顾客账号实体（归商城前台 BFF mall-bff 持有，业务域不可见）
 * <p>独立于平台管理员 sys_user、店主 store_user 的第三套账号体系；三端 id 空间由身份维度
 * userType（admin/store/user）经 Redis 键与 JWT type claim 隔离，不产生 id 冲突。
 * 「账号即手机号」：phone 是登录账号（唯一），username 不是独立列——导出到 LoginUser 快照与
 * CurrentUserVO 时 username 与 phone 同值（见 AuthService）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_user")
public class MallUser extends BaseEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 手机号（登录账号，唯一）
     */
    private String phone;

    /**
     * 密码（BCrypt 加密）
     * <p>本期为占位列：C 端走短信验证码登录，不读写本列；建列是为了与 store_user 形状一致，
     * 将来加密码登录/改密时无需迁移表。查询、返回时不暴露。</p>
     */
    @TableField(select = false)
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 状态：1 启用，0 停用
     */
    private Integer status;
}
