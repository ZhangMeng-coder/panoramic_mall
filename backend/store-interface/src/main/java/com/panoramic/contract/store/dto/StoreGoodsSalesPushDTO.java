package com.panoramic.contract.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 交易侧销量入账参数（store 域内部接口与调用方同源共享）。
 * <p>用途：<b>trade-center 在订单完成（{@code OrderStatus.RECEIVED}）后</b>把该单各 SPU 的销量推给本域
 * ——销量是 {@code store_goods_spu} 上的事实，交易域不存第二份（cross-cutting 第 24 条）。</p>
 * <p>⚠ <b>没有 {@code orderNo} 字段</b>：订单号是<b>资源标识</b>、走路径变量（cross-cutting 第 23 条），
 * 与 {@code revertStockByOrder} 同款，不并入 DTO。</p>
 * <p>⚠ <b>没有作用域字段</b>：调用方是<b>域</b>（trade-center），按资源 id 操作，域内不判身份、
 * 不校验商品归属、不判锁定与上下架——推送是「把已发生的事实记下来」，不是一次受权限约束的写。</p>
 * <p><b>幂等</b>：台账唯一键 {@code (order_no, spu_id)}，<b>重复推送是 no-op 成功</b>（不是错误）——
 * 补推任务会重放，这与库存回补 {@code revertByOrder} 的「撞键 fail-closed」<b>刻意相反</b>。
 * 故调用方可以放心重发。</p>
 */
@Data
public class StoreGoodsSalesPushDTO {

    /**
     * 销量明细（必填，至少一行；每行是「一个 SPU 在该单里的合计件数」）
     */
    @Valid
    @NotEmpty(message = "销量明细不能为空")
    private List<StoreGoodsSalesItemDTO> items;
}
