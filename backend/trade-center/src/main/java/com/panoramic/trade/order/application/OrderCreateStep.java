package com.panoramic.trade.order.application;

import com.panoramic.trade.order.domain.OrderModel;

/**
 * 订单生成步骤（可插拔）：一个步骤一件事，跑哪些、什么顺序由配置 {@code panoramic.trade.order.steps} 决定（裁定 D9）。
 *
 * <p>⚠ <b>为什么入参只有 {@link OrderModel} 本体</b>（todo 原话：「参数使用 OrderModel 本体传参」）：
 * 步骤之间不互相认识、不共享局部变量，唯一能传递的就是聚合根本身。这条约束换来两件事——
 * ① 步骤可以胡乱增删、换顺序（只要不违反模型自身的不变量），装配层不必跟着改；
 * ② 「步骤少跑了一个」这件事在模型上留下痕迹（未 seal、行未补全），在 seal 时统一被拦（见 {@code OrderModel}）。
 * 代价是步骤之间会重复查询（goods-check 与 price-compute 各查一次商品）——**这是刻意的取舍**：
 * 让每一步自洽、不依赖前置步骤的非模型产出，比省一次查询重要。</p>
 *
 * <p>⚠ <b>{@link #name()} 是配置与本类之间的唯一契约</b>：它出现在 yml 的 {@code steps} 列表里，
 * 故必须<b>稳定</b>（改名等于改配置）且<b>唯一</b>（重名会让「配置里写的是哪一个」变得无法回答，
 * 装配期直接抛错）。新增一个自定义步骤 = 新增一个 {@code @Component} 实现 + 在 yml 的 {@code steps} 里写上它的名字，
 * 不需要改任何既有代码。</p>
 *
 * <p>⚠ 步骤里**不做事务、不做补偿**：扣库存这类「做了一半」的中间态由编排层统一回补
 * （{@code OrderCreateCoordinator}，裁定 D4/D13）——步骤自己回滚只会让「谁负责什么」变模糊。</p>
 */
public interface OrderCreateStep {

    /**
     * 步骤名（配置里写的就是它；必须全局唯一且稳定）
     *
     * @return 步骤名，如 {@code goods-check} / {@code stock-check} / {@code price-compute}
     */
    String name();

    /**
     * 执行本步骤（就地补全 / 校验传入的订单）
     *
     * @param order 待处理的订单（未 seal 的中间态；步骤通过聚合方法补全它）
     * @throws com.panoramic.common.exception.ServiceException 业务校验失败（HTTP 400，可原样透传给页面）
     */
    void execute(OrderModel order);
}
