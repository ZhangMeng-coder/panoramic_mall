package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 取消订单请求参数：只承载数据权限锚点（这个动作没有别的载荷）。
 *
 * <p>⚠ <b>只有「待支付」能取消</b>：闸门在域侧（取消这个结束过程声明的来源状态就是「待支付」，
 * 见 {@code OrderModel#markCancelled}），已支付的单要走的是{@link TradeOrderRefundDTO 仅退款}，
 * 不是取消——「取消」与「退款」在钱上有区别（前者没收到过钱、后者要把钱退回去），故是两个动作。
 * 非待支付回 400，提示语可直接展示。</p>
 *
 * <p>⚠ 取消**一律回补库存**（下单即扣的库存在这里还回去），故本动作在域侧是跨服务写、带全局事务。</p>
 *
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：写操作的作用域<b>没有</b>
 * 「合法全量视角」，省掉它就是「有权限取消任意一笔单」。值只能由端 BFF 从登录态取
 * （{@code LoginUser.getId()}），**禁止**从前端入参透传。</p>
 *
 * <p>⚠ 本期**不记取消原因**：需求里没有这一项，而加一个没人读的列就是给「以后会有用」预留的空洞。
 * 要加时按表结构变更走（备份 + 加列）。</p>
 */
@Data
public class TradeOrderCancelDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;
}
