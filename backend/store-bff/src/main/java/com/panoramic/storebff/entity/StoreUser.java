package com.panoramic.storebff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.panoramic.common.vo.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店主账号实体（归店铺端 BFF store-bff 持有，store 域不可见）
 * <p>独立于平台管理员 sys_user 的账号体系；两类用户 id 空间由鉴权 userType 维度
 * （admin/store）经 Redis 键与 JWT type claim 隔离，不产生 id 冲突。
 * 「账号店同 ID」：店主账号 id 即其店铺主键（store 域 store_shop.id），本表不新增指针列。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("store_user")
public class StoreUser extends BaseEntity {

    /**
     * 主键（=其店铺主键，一人一店）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（登录账号）
     */
    private String username;

    /**
     * 密码（BCrypt 加密）
     * <p>仅新建/改密时写入；查询、返回时不暴露。</p>
     */
    @TableField(select = false)
    private String password;

    /**
     * 昵称/姓名
     */
    private String nickname;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 状态：1 启用，0 停用
     */
    private Integer status;
}
