package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.store.entity.StoreUser;

/**
 * 店主账号服务
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；新增方法仅承载业务规则。</p>
 */
public interface StoreUserService extends IService<StoreUser> {

    /**
     * 按用户名判断是否存在（注册重名校验）
     *
     * @param username 用户名
     * @return true=已存在
     */
    boolean existsByUsername(String username);

    /**
     * 按用户名取登录账号（显式带上密码列，供登录校验）
     *
     * @param username 用户名
     * @return 账号实体；不存在返回 null
     */
    StoreUser getForAuthByUsername(String username);
}
