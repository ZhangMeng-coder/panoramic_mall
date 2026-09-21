package com.panoramic.trade.order.domain.port;

import com.panoramic.trade.order.domain.OrderModel;

import java.util.List;

/**
 * 一次下单提交的记录（第一级幂等的落点）：{@code requestId} → **当时返回给调用方的那一整批订单**。
 *
 * <p>⚠ <b>为什么必须单独记一份，而不去订单行上按 requestId 查</b>：一次提交可能拆成多笔（一单一店），
 * 其中「指纹命中复用」的那几笔属于**上一次提交**——它们的 {@code requestId} 记的是「哪次提交创造了这笔单」，
 * 不能改写成本次的（改了就把上一次的凭证抹掉了）。于是「本次提交包含哪些笔」在订单行上根本不成立：
 * 按 requestId 查只会查到本次**新建**的笔，重放时少返回复用的笔——
 * 同一个请求两次调用返回的条数都不一样，客户端只会以为丢了单。</p>
 *
 * <p>⚠ <b>{@code requestId} 的作用域是「顾客内」</b>：幂等键由客户端生成，不同顾客之间不共享。
 * 只按键查会让 A 顾客的订单被 B 顾客用同一个键捞走（订单号 / 金额 / 门店全外泄），
 * 故查询必须连 {@code customerId} 一起收窄（{@link OrderRepository#findSubmission}）。</p>
 *
 * @param requestId  调用方带来的幂等键（可为 null：为空表示这次提交不做请求级去重，也就不记映射）
 * @param customerId 下单顾客 id（与 {@code requestId} 一起构成查询键）
 * @param orders     这次提交返回的**整批**订单（含复用笔），顺序与首次返回一致。⚠ 存的是**活引用不是快照**（见下）
 */
public record OrderSubmission(String requestId, Long customerId, List<OrderModel> orders) {

    /**
     * ⚠ 只拷**列表**、不拷元素：这份记录持有的是那批订单的**活引用**，不是当时的快照。
     *
     * <p>于是重放返回的是它们的**当前**状态（订单生命周期会自己推进，重放看到最新真相，不会拿到过期状态），
     * 代价是「这份记录是唯一凭证」只成立于**没人改它**的前提下：返回值与仓库里是同一批对象，
     * 谁改了返回值就等于改了凭证。故**调用方不得修改**返回的订单，本类也不自称快照。</p>
     */
    public OrderSubmission {
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
}
