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
        //
        // ⚠ 已知取舍：`ChainUpdate#update()` 走的是 `update(null)`，**不触发审计自动填充**，
        //    故本支路不会刷新 update_user（update_time 由 DDL 的 ON UPDATE CURRENT_TIMESTAMP 推进）。
        //    当前无可观测影响——唯一写者就是顾客本人，值与 insert 时相同。
        //    而**不能**改成 `update(entity, wrapper)` 换回自动填充：TableFieldInfo#getSqlSet 对业务列
        //    默认套 `convertIf(..., updateStrategy)`（NOT_NULL），那样四列里的 null 会被跳过，
        //    「清空」语义直接丢——即两者在 MP 里互斥，本处选择保清空。（审计列 withUpdateFill=true，
        //    恰是唯一不被 if 包裹的，这也是 `update(entity, wrapper)` 能刷新审计的原因。）
        //    store 域 8 处写 null 的场景用的是同一写法，本处与仓库既有口径一致。
        lambdaUpdate()
                .eq(CustomerProfile::getId, customerId)
                .set(CustomerProfile::getNickname, dto.getNickname())
                .set(CustomerProfile::getAvatar, dto.getAvatar())
                .set(CustomerProfile::getGender, dto.getGender())
                .set(CustomerProfile::getBirthday, dto.getBirthday())
                .update();
    }
}
