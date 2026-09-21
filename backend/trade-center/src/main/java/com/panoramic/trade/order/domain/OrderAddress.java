package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;

/**
 * 收货地址快照（值对象）：下单那一刻的收件人 / 电话 / 地区 / 详址。
 *
 * <p>⚠ <b>存快照而不是 {@code addressId}</b>：addressId 属于 customer-center（顾客域），
 * 而每个域只依赖自己的 {@code <域>-interface}——trade-center **结构上拿不到**顾客地址，
 * 也不该为一个只读的地址去引另一个域。归属校验与取地址由 mall-bff 做完再把快照传进来
 * （见 docs/contracts/trade-center.md 第三节）。</p>
 *
 * <p>⚠ 快照是**下单当时**的地址：此后顾客改地址 / 删地址都不影响已下的单——这正是存快照的理由。
 * 订单要寄到哪儿，是下单那一刻确定的事实，不是「顾客现在把默认地址改成了哪里」。</p>
 *
 * <p>⚠ 校验用 {@link ServiceException}(400)：地址最终是顾客在页面上填的，太长 / 为空是**用户输入问题**，
 * 该原样透传到页面（4xx 透传见 cross-cutting 第 13 条），而不是落库时炸成 500（那是数据截断错误的形状）。
 * 长度上限与 {@code trade_order} 的四列一一对应（列窄于此处即写不进去，故两边必须同口径）。</p>
 *
 * @param receiverName  收件人
 * @param receiverPhone 收件人电话
 * @param region        省市区（BFF 从顾客地址拼好传来，域内不解析行政区划）
 * @param detail        详细地址
 */
public record OrderAddress(String receiverName, String receiverPhone, String region, String detail) {

    /** 收件人姓名长度上限（= {@code trade_order.receiver_name}） */
    public static final int MAX_NAME_LENGTH = 32;

    /** 电话长度上限（= {@code trade_order.receiver_phone}） */
    public static final int MAX_PHONE_LENGTH = 20;

    /** 地区长度上限（= {@code trade_order.receiver_region}） */
    public static final int MAX_REGION_LENGTH = 128;

    /** 详址长度上限（= {@code trade_order.receiver_detail}） */
    public static final int MAX_DETAIL_LENGTH = 255;

    /**
     * 紧凑构造器：四个字段一律「去空白 → 非空 → 不超长」
     *
     * <p>存进去的是**去过空白**的值，故「长度校验」与「落库值」是同一个字符串，
     * 不会出现「32 个字符里混着首尾空白、落库时被 MySQL 截掉尾巴」这类两边不一致。</p>
     *
     * @throws ServiceException 任一字段为空（HTTP 400，可原样展示给顾客）
     */
    public OrderAddress {
        receiverName = require(receiverName, "收件人", MAX_NAME_LENGTH);
        receiverPhone = require(receiverPhone, "收件人电话", MAX_PHONE_LENGTH);
        region = require(region, "所在地区", MAX_REGION_LENGTH);
        detail = require(detail, "详细地址", MAX_DETAIL_LENGTH);
    }

    /**
     * 取值并校验一个字段
     *
     * @param value     原值（可为 null）
     * @param label     字段的中文名（进消息，直接展示给顾客）
     * @param maxLength 长度上限
     * @return 去掉首尾空白后的值
     * @throws ServiceException 去空白后为空或超长
     */
    private static String require(String value, String label, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ServiceException(400, "收货地址的" + label + "不能为空");
        }
        if (trimmed.length() > maxLength) {
            throw new ServiceException(400, "收货地址的" + label + "不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }
}
