package com.panoramic.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.customer.dto.CustomerAddressSaveDTO;
import com.panoramic.contract.customer.vo.CustomerAddressVO;
import com.panoramic.customer.entity.CustomerAddress;
import com.panoramic.customer.mapper.CustomerAddressMapper;
import com.panoramic.customer.service.CustomerAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务实现（customer-center 域下沉纯域）。
 * <p>所有读写一律以「id + customerId」双条件限定作用域（域内不做鉴权，锚点是唯一防线）；
 * 不属于该顾客的地址一律抛 404「地址不存在」，不区分「不存在」与「不归属」以免泄露存在性。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本类**不显式赋值**（Global Constraint 3）。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerAddressServiceImpl extends ServiceImpl<CustomerAddressMapper, CustomerAddress>
        implements CustomerAddressService {

    /**
     * 单顾客地址条数上限（spec §6.4）
     */
    private static final int MAX_ADDRESS_COUNT = 20;

    @Override
    public List<CustomerAddressVO> listAddresses(Long customerId) {
        return lambdaQuery()
                .eq(CustomerAddress::getCustomerId, customerId)
                .orderByDesc(CustomerAddress::getIsDefault)   // 默认地址排最前
                .orderByDesc(CustomerAddress::getId)          // 同默认位时新的在前
                .list()
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public CustomerAddressVO getAddress(Long customerId, Long id) {
        return toVo(requireOwned(customerId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveAddress(Long customerId, CustomerAddressSaveDTO dto) {
        long count = lambdaQuery().eq(CustomerAddress::getCustomerId, customerId).count();
        if (count >= MAX_ADDRESS_COUNT) {
            throw new ServiceException("收货地址最多 20 条");
        }
        CustomerAddress entity = new CustomerAddress();
        entity.setCustomerId(customerId);
        entity.setReceiverName(dto.getReceiverName());
        entity.setReceiverPhone(dto.getReceiverPhone());
        entity.setRegion(dto.getRegion());
        entity.setDetailAddress(dto.getDetailAddress());
        entity.setIsDefault(count == 0 ? 1 : 0);   // 首条自动默认：设默认只剩「首条」与 setDefaultAddress 两条路径
        save(entity);                              // 走 MP 基类 → 触发审计自动填充
        return entity.getId();                     // IdType.AUTO：id 由 DB 生成后回填
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long customerId, Long id, CustomerAddressSaveDTO dto) {
        requireOwned(customerId, id);
        // ⚠ 刻意**不含 isDefault**（spec §5.6）：设默认只有两条路径，新增/编辑不得绕过默认唯一性的清位事务。
        // ⚠ 用 lambdaUpdate().set(...) 而非 updateById(entity)：updateById 跳过 null 列，会把「清空省市区」静默丢掉
        //    （同 T3 资料保存、store 域 refreshMinPrice 的坑）；代价是该支路不触发审计自动填充
        //    （ChainUpdate#update() 走 update(null)），当前唯一写者是顾客本人，无可观测影响。
        lambdaUpdate()
                .eq(CustomerAddress::getId, id)
                .eq(CustomerAddress::getCustomerId, customerId)   // 锚点：域内不做鉴权，作用域只能靠它
                .set(CustomerAddress::getReceiverName, dto.getReceiverName())
                .set(CustomerAddress::getReceiverPhone, dto.getReceiverPhone())
                .set(CustomerAddress::getRegion, dto.getRegion())
                .set(CustomerAddress::getDetailAddress, dto.getDetailAddress())
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long customerId, Long id) {
        requireOwned(customerId, id);
        // 逻辑删除（@TableLogic）；⚠ 删掉默认地址后**不递补**（spec D7）——不在此顺手改别的行的 is_default
        removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultAddress(Long customerId, Long id) {
        requireOwned(customerId, id);                  // 不存在/不归属 → 404
        // MySQL 8 无部分唯一索引可用，只能应用层保证「同一顾客至多一条 is_default=1」：
        // 同事务内先把该顾客所有行清 0，再置目标行为 1。
        //
        // ⚠ 为什么先加这一把行锁（不是多余的一次查询）：两笔「设默认」只做「清 0 + 置 1」时存在已知交错序列——
        //    后到者的清 0 可能在前一笔的置 1 提交之前就扫完（当时没匹配到行）→ 两行最终都是 1。
        //    这里先对该顾客名下全部地址行取排他锁，把同一顾客的并发设默认**串行化**：后到者会阻塞到前一笔提交，
        //    其后的清 0 是当前读、能看见已提交的最新默认位，必然把先到者置的 1 清掉。
        //    锁范围严格限定在 customer_id 之内（一个顾客 ≤ 20 行、且都是本顾客自己的数据），不跨顾客、不升表锁。
        lambdaQuery()
                .select(CustomerAddress::getId)
                .eq(CustomerAddress::getCustomerId, customerId)
                .last("FOR UPDATE")
                .list();
        lambdaUpdate()
                .eq(CustomerAddress::getCustomerId, customerId)
                .eq(CustomerAddress::getIsDefault, 1)
                .set(CustomerAddress::getIsDefault, 0)
                .update();
        lambdaUpdate()
                .eq(CustomerAddress::getId, id)
                .eq(CustomerAddress::getCustomerId, customerId)   // ⚠ 必须带 customerId：只按 id 更新等于开了越权写入口
                .set(CustomerAddress::getIsDefault, 1)
                .update();
    }

    /**
     * 取「属于该顾客的地址」，不属于一律抛 404（不区分「不存在」与「不归属」，不泄露存在性）
     */
    private CustomerAddress requireOwned(Long customerId, Long id) {
        CustomerAddress entity = lambdaQuery()
                .eq(CustomerAddress::getId, id)
                .eq(CustomerAddress::getCustomerId, customerId)
                .one();
        if (entity == null) {
            throw new ServiceException(404, "地址不存在");
        }
        return entity;
    }

    /**
     * 实体 → 视图对象
     */
    private CustomerAddressVO toVo(CustomerAddress entity) {
        CustomerAddressVO vo = new CustomerAddressVO();
        vo.setId(entity.getId());
        vo.setReceiverName(entity.getReceiverName());
        vo.setReceiverPhone(entity.getReceiverPhone());
        vo.setRegion(entity.getRegion());
        vo.setDetailAddress(entity.getDetailAddress());
        vo.setIsDefault(entity.getIsDefault());
        return vo;
    }
}
