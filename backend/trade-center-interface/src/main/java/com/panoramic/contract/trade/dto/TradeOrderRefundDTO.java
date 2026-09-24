package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 仅退款请求参数：只承载数据权限锚点（这个动作没有别的载荷）。
 *
 * <p>⚠ <b>只有「已支付、未发货」能仅退款</b>：闸门在域侧（仅退款这个结束过程声明的来源状态就是
 * 「已支付」，见 {@code OrderModel#markRefunded}）。已发货的单不在本期口径内（那要牵扯退货收货，不是「仅退款」）；
 * 未支付的单该走{@link TradeOrderCancelDTO 取消}。非该状态回 400，提示语可直接展示。</p>
 *
 * <p>⚠ <b>一步生效、无需商户同意</b>（本期裁定）：顾客发起即生效，域侧不设「商户审批」中间态——
 * 那会多出一个状态与一条待办，而本期的资金是假的（假支付），先按最简单的口径落地。</p>
 *
 * <p>⚠ <b>全额退</b>：不传金额。退款金额恒等于订单总额（聚合里冻结的那个值），
 * 让调用方传金额等于给「退多少」开出第二个说了算的地方。</p>
 *
 * <p>⚠ 退款**一律回补库存**（下单即扣的库存在这里还回去），故本动作在域侧是跨服务写、带全局事务。</p>
 *
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：写操作的作用域<b>没有</b>
 * 「合法全量视角」，省掉它就是「有权限把任意一笔单退款」。值只能由端 BFF 从登录态取
 * （{@code LoginUser.getId()}），**禁止**从前端入参透传。</p>
 */
@Data
public class TradeOrderRefundDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;
}
