package com.panoramic.storebff.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.storebff.entity.StoreUser;
import com.panoramic.storebff.mapper.StoreUserMapper;
import com.panoramic.storebff.service.StoreUserService;
import org.springframework.stereotype.Service;

/**
 * 店主账号服务实现
 * <p>own-entity 增删改查全部走 MyBatis-Plus 基类，仅新增业务规则方法。</p>
 */
@Service
public class StoreUserServiceImpl extends ServiceImpl<StoreUserMapper, StoreUser> implements StoreUserService {

    @Override
    public boolean existsByUsername(String username) {
        return count(Wrappers.<StoreUser>lambdaQuery()
                .eq(StoreUser::getUsername, username)) > 0;
    }

    @Override
    public StoreUser getForAuthByUsername(String username) {
        // 显式 select 带上 password 列（实体上 @TableField(select=false) 默认不查），供登录校验使用
        return getOne(Wrappers.<StoreUser>lambdaQuery()
                .select(StoreUser::getId, StoreUser::getUsername, StoreUser::getPassword,
                        StoreUser::getNickname, StoreUser::getPhone, StoreUser::getStatus)
                .eq(StoreUser::getUsername, username));
    }
}
