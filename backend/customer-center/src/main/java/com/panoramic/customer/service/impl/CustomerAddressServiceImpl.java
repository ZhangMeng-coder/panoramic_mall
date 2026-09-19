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
        // ⚠ 这一个 count **同时**决定两件事，别只看上限那件：
        //    ① 20 条上限——**软上限**：并发下两笔可双双通过检查、多插一行，无害；
        //    ② isDefault——**真不变量**（spec §6.4 / DDL 列注释 / README C3「同一顾客至多一条 is_default=1」）：
        //       地址簿为空时（新注册，或按 D7 把地址全删光）两笔并发新增会双双 count==0 → **两条都是默认**。
        //    ⚠ 本支路**关不上这个窗口**，也不能照抄 setDefaultAddress 的加固：空地址簿**没有行可锁**，
        //       两个事务的间隙锁彼此兼容、互不阻塞，且后到者被阻塞时 is_default 早已按自己看到的 count 决定完。
        //       彻底解法是 DB 层的部分唯一索引（MySQL 8 函数索引），属表结构与已批准设计（spec §6.4 选了应用层事务）
        //       的改动，未在本批次采纳——故本窗口如实记录，**不宣称「不变量已由应用层守住」**。
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
        entity.setIsDefault(count == 0 ? 1 : 0);   // 首条自动默认：设默认只剩「首条」与 setDefaultAddress 两条路径（并发窗口见上）
        save(entity);                              // 走 MP 基类 → 触发审计自动填充
        return entity.getId();                     // IdType.AUTO：id 由 DB 生成后回填
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long customerId, Long id, CustomerAddressSaveDTO dto) {
        requireOwned(customerId, id);
        // ⚠ 刻意**不含 isDefault**（spec §5.6）：设默认只有两条路径，新增/编辑不得绕过默认唯一性的清位事务。
        //    这条约束还有一层**并发侧**价值：四个业务列与 is_delete 都**不在** idx_customer_default 里，
        //    故本支路**从不改索引列**——不像 setDefaultAddress 那样「先动二级索引、再动 PK 行」，
        //    不存在与 FOR UPDATE 路径锁序相反的可能，等于**顺带关掉了一条经典死锁通道**（改这些列等于把它重新打开）。
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
        // 逻辑删除（@TableLogic）；⚠ 删掉默认地址后**不递补**（spec D7）——不在此顺手改别的行的 is_default。
        // ⚠ 锚点**写进删除语句本身**（而非只靠上面的 requireOwned）：域内不做鉴权，锚点是唯一防线，
        //    这是本 service 六个方法里唯一「归属只在先验语句里」的形状——将来的「转移地址归属 / 平台代删」
        //    会把它变成越权删除，而编译期与检查器都拦不住这种退化。语义与 removeById(id) 相同（重复删除幂等）。
        remove(lambdaQuery()
                .eq(CustomerAddress::getId, id)
                .eq(CustomerAddress::getCustomerId, customerId));
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
        //    ⚠ 锁范围**按 customer_id 收敛、不依赖执行计划**：走 idx_customer_default 时 ≈ 本顾客全部行
        //    **+ 一个相邻 gap**（next-key 锁的尾 gap 会越过顾客边界，只造成邻号顾客的 INSERT 短暂阻塞，
        //    不写坏数据）；但本语句的 WHERE 只有 customer_id，**若优化器选全表扫描则退化为整表锁**——
        //    锁范围取决于计划，不要把它当成静态事实。
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
        // ⚠ 置 1 的受影响行数**必须判**：目标行若在 requireOwned 与本次置位之间被并发删除，
        //    FOR UPDATE 会先阻塞到对方提交、随后该行被 is_delete=0 滤出 → 两条 UPDATE 都不匹配，
        //    结果是「旧默认已清掉、新默认没置上」却成功返回。终态「无默认」按 D7 合法（不是不变量问题），
        //    但成功回执与实际不符（调用方以为切换成功），故为 0 行时按「地址不存在」回 404。
        boolean set = lambdaUpdate()
                .eq(CustomerAddress::getId, id)
                .eq(CustomerAddress::getCustomerId, customerId)   // ⚠ 必须带 customerId：只按 id 更新等于开了越权写入口
                .set(CustomerAddress::getIsDefault, 1)
                .update();
        if (!set) {
            throw new ServiceException(404, "地址不存在");
        }
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
