package com.panoramic.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.customer.entity.CustomerProfile;
import com.panoramic.customer.mapper.CustomerProfileMapper;
import com.panoramic.customer.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 顾客资料服务实现（customer-center 域下沉纯域）。
 * <p>资料与顾客账号一对一（主键 = {@code mall_user.id}），读走「无记录返回空 VO」、写走「无记录则建行」
 * 的惰性口径——调用方（mall-bff）不必先建资料行，也拿不到 null。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）一律由 common 的
 * {@code MyMetaObjectHandler} 经 {@code UserContext} 自动填充，本类**不显式赋值**（Global Constraint 3）。</p>
 */
@Slf4j
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
        if (entity == null) {
            CustomerProfileVO empty = new CustomerProfileVO();
            empty.setId(customerId);
            return empty;
        }
        return toVO(entity);
    }

    /**
     * 惰性创建：无记录则 INSERT（显式写 id=customerId），有则 UPDATE
     * <p>⚠ INSERT 支路**接住 `DuplicateKeyException` 后回落 UPDATE**：`getById` 判空与 `save` 之间
     * 没有锁，并发的首次保存会双双走 INSERT、后者撞主键（详见 catch 处的论证）。</p>
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
            try {
                save(entity);                   // 走 MP 基类 → 触发审计自动填充
                return;
            } catch (DuplicateKeyException e) {
                // ⚠ 接住并发 upsert 的窗口：同一账号**两个标签页首次**保存资料时都读到 null → 双双走 INSERT，
                //    后者撞主键。不接住的话域兜底回 HTTP 500 → BFF 降级成「顾客资料暂不可用」，
                //    且**按 5xx 计入熔断失败率**（连点几次就能把熔断打开，此后正常请求也被降级）。
                //    就地落到下面的 UPDATE 支路。**为什么这样安全**（两条缺一不可）：
                //    ① 本方法**没有内层 `@Transactional` 边界**，异常在方法体里被捕获、不穿过事务代理，
                //       Spring 不会把外层事务标成 rollback-only（标了的话后续 UPDATE 会以
                //       `UnexpectedRollbackException` 收场，那才是真事故）；
                //    ② MySQL / InnoDB 的重复键错误**只让当前语句失败、不中止整个事务**（与 PostgreSQL 不同），
                //       随后的 UPDATE 可正常执行并提交。先到者已提交的那一行，正是我们要写的目标行。
                log.info("顾客资料并发建行，回落到更新支路（customerId={}）", customerId);
            }
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

    /**
     * 批量读资料：一条 {@code IN} 查询，SQL 条数与 id 个数无关。
     * <p>⚠ <b>查不到的 id 跳过</b>（不做逐 id 补空 VO）：这是与单条 {@link #getProfile} 的有意分歧——
     * 调用方（评价列表）按「拿不到 = 无资料」走占位展示，补空 VO 只会让它多写一次「这人到底有没有资料行」的判断。</p>
     * <p>⚠ 空集合直接返回空列表：{@code IN ()} 是语法错误，且「没有要查的人」本就不必发 SQL。</p>
     * <p>⚠ 去重交给 SQL（{@code IN} 对重复 id 天然只命中一次）——调用方不必先 {@code distinct}
     * （评价列表同一顾客可能有多条评价，重复 id 是常态）。</p>
     */
    @Override
    public List<CustomerProfileVO> listProfilesByIds(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        List<CustomerProfile> entities = listByIds(customerIds);
        List<CustomerProfileVO> result = new ArrayList<>(entities.size());
        for (CustomerProfile entity : entities) {
            result.add(toVO(entity));
        }
        return result;
    }

    /**
     * 实体 → VO 的<b>唯一</b>映射处（单条与批量共用，避免两处字段清单各写一份而漂移）。
     */
    private static CustomerProfileVO toVO(CustomerProfile entity) {
        CustomerProfileVO vo = new CustomerProfileVO();
        vo.setId(entity.getId());
        vo.setNickname(entity.getNickname());
        vo.setAvatar(entity.getAvatar());
        vo.setGender(entity.getGender());
        vo.setBirthday(entity.getBirthday());
        return vo;
    }
}
