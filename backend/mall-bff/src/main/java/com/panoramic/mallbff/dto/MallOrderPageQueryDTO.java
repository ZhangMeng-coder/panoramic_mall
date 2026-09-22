package com.panoramic.mallbff.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的订单分页查询参数（页面级，{@code POST /orders/page} 的入参）。
 *
 * <p>⚠ <b>没有 {@code customerId} 字段</b>（cross-cutting 第 22 条）：作用域<b>不出现</b>在页面契约里
 * ——它在 service 里被<b>无条件</b>写成登录态（C 端只有「我的订单」一个视角，页面无权选择看谁的单）。
 * 前端能传来的锚点等于把数据权限交给页面。</p>
 *
 * <p>⚠ 分页字段（{@code pageNum} / {@code pageSize}）继承自 {@link BasePageVO}，
 * 约束落在默认组——controller 里必须用<b>裸 {@code @Valid}</b>（理由见
 * {@code CatalogController#goods}）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallOrderPageQueryDTO extends BasePageVO {

    /**
     * 订单号<b>精确</b>匹配；不填则不筛
     */
    private String orderNo;

    /**
     * 订单状态（枚举名，如 {@code PAID}）；不填则不筛
     * <p>⚠ 用 {@code String}：状态枚举在域内，契约包不立第二份；取值由域内解析，别的值回 400。</p>
     */
    private String status;
}
