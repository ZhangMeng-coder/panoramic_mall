package com.panoramic.storebff.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 店铺端 BFF · 商户侧订单分页查询参数（**页面入参**，本层私有）。
 *
 * <p>⚠ <b>没有 {@code storeId} 字段</b>（cross-cutting 第 22 条）：作用域<b>不出现</b>在页面契约里
 * ——它在 {@code StoreOrderBffService} 里被<b>无条件</b>写成登录态（店主侧只有「本店订单」一个视角，
 * 页面无权选择看哪家店）。页面能传来的锚点等于把数据权限交给页面。</p>
 *
 * <p>⚠ 与域侧入参 {@code TradeOrderPageQueryDTO} 是<b>两份</b>类型：域侧那份带作用域字段，
 * 由本层组装后传给域，不是页面入参（口径见 {@code docs/contracts/store-bff.md} 第一节）。</p>
 *
 * <p>⚠ 分页字段（{@code pageNum} / {@code pageSize}）继承自 {@link BasePageVO}，
 * 约束落在默认组——controller 里用 {@code @Validated} 触发（与本模块 {@code StoreGoodsSpuPageQueryDTO}
 * 等 GET 分页入参同款）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StoreOrderPageQueryDTO extends BasePageVO {

    /**
     * 订单号<b>精确</b>匹配；不填则不筛
     */
    private String orderNo;

    /**
     * 订单状态（枚举名，如 {@code PAID}）；不填则不筛
     *
     * <p>⚠ 用 {@code String}：状态枚举在域内，契约包不立第二份；取值由域内解析，别的值回 400。</p>
     */
    private String status;
}
