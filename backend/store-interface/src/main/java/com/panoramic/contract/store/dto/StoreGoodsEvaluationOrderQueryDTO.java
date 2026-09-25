package com.panoramic.contract.store.dto;

import lombok.Data;

/**
 * 「某订单里已评价过哪些商品」查询参数（store 域内部接口与端 BFF 同源共享）。
 * <p>唯一调用方是 <b>mall-bff</b>：订单详情页要标出「本单哪些商品已评价、哪些还能评价」，
 * 一次取回本单的已评价 SPU id 集合，避免逐商品调评价分页去猜。</p>
 * <p>订单号是<b>路径变量</b>（资源标识，不进 DTO，cross-cutting 第 23 条），故本 DTO 只放
 * 「谁的订单」这一项。</p>
 */
@Data
public class StoreGoodsEvaluationOrderQueryDTO {

    /**
     * 评价人（= mall_user.id）；空 = 不限定。
     * <p>由 mall-bff 从登录态取。订单号本身已能定位唯一顾客，传它只是保持「按传入锚点过滤」
     * 的一致口径，顺手挡住「拿别人的单号来查已评价列表」。</p>
     */
    private Long customerId;
}
