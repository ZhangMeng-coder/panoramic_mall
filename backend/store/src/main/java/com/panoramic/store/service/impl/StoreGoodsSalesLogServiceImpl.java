package com.panoramic.store.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.store.dto.StoreGoodsSalesItemDTO;
import com.panoramic.contract.store.dto.StoreGoodsSalesPushDTO;
import com.panoramic.store.entity.StoreGoodsSalesLog;
import com.panoramic.store.mapper.StoreGoodsSalesLogMapper;
import com.panoramic.store.service.StoreGoodsSalesLogService;
import com.panoramic.store.service.StoreGoodsSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 店铺商品销量台账服务实现
 * <p>写路径单条 INSERT（外加一次 SPU 上的原子自增），读路径一次条件查询，均很薄；
 * 唯一带事务的是 {@link #pushSales}——它要写两张表（台账 + SPU 汇总列），
 * 「记了台账没加计数」与「加了计数没记台账」都不允许（同款例外见
 * {@code StoreGoodsSkuStockServiceImpl#deduct}）。</p>
 * <p>SPU 侧一律经 {@link StoreGoodsSpuService} 写（跨实体只走 owner service），本类不持 SPU Mapper。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreGoodsSalesLogServiceImpl extends ServiceImpl<StoreGoodsSalesLogMapper, StoreGoodsSalesLog>
        implements StoreGoodsSalesLogService {

    /** 跨实体：销量落在 SPU 上（汇总列 + 排序分置脏都只经它；本类不直接持有 SPU Mapper） */
    private final StoreGoodsSpuService spuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pushSales(String orderNo, StoreGoodsSalesPushDTO dto) {
        if (!StringUtils.hasText(orderNo)) {
            throw new ServiceException("订单号不能为空");
        }
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            // 入口的 @Valid @NotEmpty 已拦下；这里是服务内直调（测试 / 其它调用方）的兜底。
            // 空明细不是错误：本方法的语义是「把给定的这些行入账」，没有行就是没事可做
            return;
        }
        for (StoreGoodsSalesItemDTO item : dto.getItems()) {
            pushItem(orderNo, item);
        }
    }

    /**
     * 单行入账：已入账跳过，否则记台账后自增计数（两处同事务）。
     */
    private void pushItem(String orderNo, StoreGoodsSalesItemDTO item) {
        Long spuId = item.getSpuId();
        Integer quantity = item.getQuantity();
        if (spuId == null || quantity == null || quantity <= 0) {
            // 同上：Bean Validation 的兜底。⚠ 这里不像库存扣减那样把非法入参压成「失败返回值」——
            // 本方法没有返回值可表达「这一行没入账」，静默跳过该行、其余行照常入账即可
            return;
        }
        if (exists(orderNo, spuId)) {
            // 已入账：补推任务重放同一笔时走这里。**no-op 成功**，不抛异常（调用方据此可以放心重发）
            return;
        }
        StoreGoodsSalesLog row = new StoreGoodsSalesLog();
        row.setOrderNo(orderNo);
        row.setSpuId(spuId);
        row.setQuantity(quantity);
        try {
            // 审计列（create_user/create_time/...）由 MyMetaObjectHandler 经 save 自动填充，不手写
            save(row);
        } catch (DuplicateKeyException e) {
            // 与「同一笔的上一次推送」并发：唯一键 uk_order_no_spu 兜住了重复入账。
            // 既然那一笔已经记上了，本次按**幂等成功**处理（上面的 exists 是快路径，这里是硬保证）。
            // ⚠ 与 revertByOrder 撞键时的 fail-closed **刻意相反**：那边宁可整体回滚也不能多补库存；
            //   这边重放本来就是预期内的（补推），多记一行才是错。
            // ⚠ MySQL 下唯一键冲突**不会**中止当前事务，故 catch 之后可以继续，无需整事务重来。
            log.info("销量入账：并发重复推送，按幂等跳过。orderNo={}，spuId={}", orderNo, spuId);
            return;
        }
        // 计数与置脏都在 SPU 侧（它同时把该商品的排序分置脏）：台账已落库，事务保证两者同成同败
        spuService.addSales(spuId, quantity);
    }

    /**
     * 该单该 SPU 是否已入账（幂等判据；唯一键是硬保证，这里只是避免白撞一次键）。
     */
    private boolean exists(String orderNo, Long spuId) {
        return count(Wrappers.<StoreGoodsSalesLog>lambdaQuery()
                .eq(StoreGoodsSalesLog::getOrderNo, orderNo)
                .eq(StoreGoodsSalesLog::getSpuId, spuId)) > 0;
    }
}
