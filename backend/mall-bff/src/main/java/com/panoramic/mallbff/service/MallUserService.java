package com.panoramic.mallbff.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.mallbff.entity.MallUser;

/**
 * C 端顾客账号服务
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；新增方法仅承载业务规则。</p>
 */
public interface MallUserService extends IService<MallUser> {

    /**
     * 按手机号判断是否存在（注册查重校验）
     *
     * @param phone 手机号
     * @return true=已存在
     */
    boolean existsByPhone(String phone);

    /**
     * 按手机号取账号（登录用）
     *
     * @param phone 手机号
     * @return 账号实体；不存在返回 null
     */
    MallUser getByPhone(String phone);
}
