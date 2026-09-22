package com.panoramic.contract.trade.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单分页查询参数（**所有调用方共用一份**，cross-cutting 第 22 条）。
 *
 * <p>⚠ <b>顾客 id / 店铺 id 都在本类里、且是可选的作用域字段</b>：锚点不进路径段，以入参 DTO 的字段承载。
 * 「传了就按它筛，没传就是不限定」——订单分页是**有合法全量视角**的能力（管理端要看全部），
 * 故这两个字段可省（对照：订单四个写与购物车八条的作用域必填，那里省不掉）。</p>
 * <p>⚠ 值<b>只能由端 BFF 从登录态取</b>（{@code LoginUser.getId()}），**禁止**从前端入参透传：
 * 前端传来的 id 一旦被当作作用域，等于把数据权限交给页面。</p>
 * <p>⚠ 这两个字段是<b>作用域</b>，不是「筛选项」：顾客侧传自己的 id、商户侧传自己的店铺 id、
 * 管理端两个都不传（全量视角）。域内**不判身份**，同一口径对所有调用方一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TradeOrderPageQueryDTO extends BasePageVO {

    /**
     * 订单号<b>精确</b>匹配；不填则不筛
     */
    private String orderNo;

    /**
     * 订单状态（枚举名，如 {@code PAID}）；不填则不筛
     *
     * <p>⚠ 与 {@link TradeOrderCreateDTO#getSource()} 同理用 {@code String}：状态枚举在域内
     * （{@code OrderStatus}），契约包不再立第二份。取值由域内解析，写了别的值一律 400。</p>
     */
    private String status;

    /**
     * 顾客 id（= {@code mall_user.id}，数据**作用域**）；不填则不限定 —— 顾客侧由端 BFF 从登录态填本人 id
     */
    private Long customerId;

    /**
     * 店铺 id（= 店主账号 id，数据**作用域**）；不填则不限定 —— 商户侧由端 BFF 从登录态填本店 id
     */
    private Long storeId;
}
