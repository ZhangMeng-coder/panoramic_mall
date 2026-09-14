package com.panoramic.mallbff.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.mallbff.entity.MallUser;
import com.panoramic.mallbff.mapper.MallUserMapper;
import com.panoramic.mallbff.service.MallUserService;
import org.springframework.stereotype.Service;

/**
 * C 端顾客账号服务实现
 * <p>own-entity 增删改查全部走 MyBatis-Plus 基类，仅新增业务规则方法。</p>
 */
@Service
public class MallUserServiceImpl extends ServiceImpl<MallUserMapper, MallUser> implements MallUserService {

    @Override
    public boolean existsByPhone(String phone) {
        return count(Wrappers.<MallUser>lambdaQuery()
                .eq(MallUser::getPhone, phone)) > 0;
    }

    @Override
    public MallUser getByPhone(String phone) {
        // 密码列本期不参与登录校验（走验证码），故不显式 select；实体上 @TableField(select=false) 默认不查
        return getOne(Wrappers.<MallUser>lambdaQuery()
                .eq(MallUser::getPhone, phone));
    }
}
