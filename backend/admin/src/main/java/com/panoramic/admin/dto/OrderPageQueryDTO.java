package com.panoramic.admin.dto;

import com.panoramic.common.vo.BasePageVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理后台「订单管理」分页查询参数（**页面入参**，admin BFF 自有的编排查询对象，
 * 与 {@link ShopGoodsPageQueryDTO} 同款做法）。
 *
 * <p>⚠ <b>平台侧没有数据权限锚点</b>：管理端是全量视角，故这里的 {@code storeId} /
 * {@code customerId} 是<b>页面筛选条件</b>（在全量里过滤「某一店 / 某一顾客」的单），
 * <b>不是</b>作用域——它们<b>原样来自页面</b>，<b>不</b>从登录态覆盖（管理员的登录 id
 * 既不是店铺 id 也不是顾客 id）。这与顾客端 / 商户端<b>正好相反</b>（那边作用域只能取自
 * 登录态、页面无权选），口径见 {@code docs/contracts/admin.md} 与 cross-cutting 第 22 条。</p>
 *
 * <p>⚠ 与域侧入参 {@code TradeOrderPageQueryDTO} 是<b>两份</b>类型：本层把本类 1:1 搬进域那份
 * （字段同名同义）后传给 trade-center，不是把页面对象直接当域入参。</p>
 *
 * <p>⚠ 分页字段（{@code pageNum} / {@code pageSize}）继承自 {@link BasePageVO}，
 * 约束落在默认组——controller 里用 {@code @Validated} 触发（与本模块 GET 分页入参同款）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPageQueryDTO extends BasePageVO {

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

    /**
     * 店铺筛选（= 店主账号 id）；空为全部店铺。<b>页面筛选条件，不是作用域</b>
     */
    private Long storeId;

    /**
     * 顾客筛选（= {@code mall_user.id}）；空为全部顾客。<b>页面筛选条件，不是作用域</b>
     */
    private Long customerId;
}
