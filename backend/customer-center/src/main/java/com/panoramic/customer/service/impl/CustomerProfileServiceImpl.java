package com.panoramic.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.customer.entity.CustomerProfile;
import com.panoramic.customer.mapper.CustomerProfileMapper;
import com.panoramic.customer.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 顾客资料服务实现（customer-center 域下沉纯域）。
 * <p>资料与顾客账号一对一（主键 = {@code mall_user.id}），读走「无记录返回空 VO」、写走「无记录则建行」
 * 的惰性口径——调用方（mall-bff）不必先建资料行，也拿不到 null。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）一律由 common 的
 * {@code MyMetaObjectHandler} 经 {@code UserContext} 自动填充，本类**不显式赋值**（Global Constraint 3）。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl extends ServiceImpl<CustomerProfileMapper, CustomerProfile>
        implements CustomerProfileService {

    /**
     * 无记录返回空 VO（id 填 customerId、其余 null）：不返回 null、不抛 404 —— 调用方不必判空
     */
    @Override
    public CustomerProfileVO getProfile(Long customerId) {
        CustomerProfile entity = getById(customerId);
        CustomerProfileVO vo = new CustomerProfileVO();
        vo.setId(customerId);
        if (entity != null) {
            vo.setNickname(entity.getNickname());
            vo.setAvatar(entity.getAvatar());
            vo.setGender(entity.getGender());
            vo.setBirthday(entity.getBirthday());
        }
        return vo;
    }

    /**
     * 惰性创建：无记录则 INSERT（显式写 id=customerId），有则 UPDATE
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveProfile(Long customerId, CustomerProfileSaveDTO dto) {
        CustomerProfile exists = getById(customerId);
        if (exists == null) {
            CustomerProfile entity = new CustomerProfile();
            entity.setId(customerId);          // ⚠ IdType.INPUT：必须显式写
            entity.setNickname(dto.getNickname());
            entity.setAvatar(dto.getAvatar());
            entity.setGender(dto.getGender());
            entity.setBirthday(dto.getBirthday());
            save(entity);                       // 走 MP 基类 → 触发审计自动填充
            return;
        }
        // ⚠ 用 lambdaUpdate().set(...) 而非 updateById：updateById 跳过 null 列，
        //    会把「清空昵称/头像」静默丢掉（与 store 域 refreshMinPrice 同一坑）
        lambdaUpdate()
                .eq(CustomerProfile::getId, customerId)
                .set(CustomerProfile::getNickname, dto.getNickname())
                .set(CustomerProfile::getAvatar, dto.getAvatar())
                .set(CustomerProfile::getGender, dto.getGender())
                .set(CustomerProfile::getBirthday, dto.getBirthday())
                .update();
    }
}
