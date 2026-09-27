package com.panoramic.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.store.dto.StoreGoodsSalesPushDTO;
import com.panoramic.store.entity.StoreGoodsSalesLog;

/**
 * 店铺商品销量台账服务（{@code store_goods_sales_log} 的<b>唯一 owner</b>）。
 * <p><b>只增不改</b>：台账是历史事实，本接口只暴露一个入账入口，不提供 update / remove
 * （销量没有回退通路，见 {@link StoreGoodsSalesLog}）；故「要记销量」一律经本接口，
 * 其它服务不得自行持有台账 Mapper。</p>
 *
 * <h3>它同时是「销量」这一推导量的编排点</h3>
 * <p>台账是事实、{@code store_goods_spu.sales_count} 是它的汇总冗余列——两者必须同成同败，
 * 故「记台账 + 自增计数 + 置脏排序分」三步都在本方法的一个事务里。这与评价服务把「写评价 + 重算
 * 评分」收在一处是同一个形态：<b>事实表的 owner 负责把下游推导量带上</b>，
 * 调用方（trade-center）只推事实，不认识 {@code sales_count} 与排序分。</p>
 *
 * <p>⚠ 本服务不认识 {@code store_id}、不做归属校验、不判锁定与上下架：调用方是<b>域</b>（trade-center），
 * 按资源 id 操作（cross-cutting 第 24 条）。推送的内容是「已完成的订单买了什么」，不是一次受权限约束的写。</p>
 */
public interface StoreGoodsSalesLogService extends IService<StoreGoodsSalesLog> {

    /**
     * 按单入账销量（域间推送入口，<b>幂等</b>）。
     * <p>逐行判断 {@code (orderNo, spuId)} 是否已入账：已入账则整行跳过（补推重放是 no-op 成功，
     * 不是错误）；未入账则记台账 + 自增 {@code sales_count} + 置脏排序分。</p>
     * <p>⚠ <b>不回退</b>：本方法只有「加」一个方向。销量口径是「订单完成」
     * （{@code OrderStatus.RECEIVED}），而已完成的订单不能再取消 / 退款，故不存在需要减的场景。</p>
     *
     * @param orderNo 订单号（资源标识，走路径变量；空则抛业务错误）
     * @param dto     销量明细（每行是一个 SPU 在该单里的合计件数）
     */
    void pushSales(String orderNo, StoreGoodsSalesPushDTO dto);
}
