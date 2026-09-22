package com.panoramic.contract.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 确认收货请求参数：只承载数据权限锚点（这个动作没有别的载荷）。
 *
 * <p>⚠ <b>锚点 {@code customerId} 必填</b>（cross-cutting 第 22 条）：写操作的作用域<b>没有</b>
 * 「合法全量视角」，省掉它就是「有权限把任意一笔单置为已收货」。值只能由端 BFF 从登录态取
 * （{@code LoginUser.getId()}），**禁止**从前端入参透传。</p>
 *
 * <p>⚠ 为什么<b>不</b>复用详情那份 {@link TradeOrderQueryDTO}：那份的两个作用域字段是**可选**的
 * （详情对管理端是全量视角），拿它当写接口的入参等于让「漏传锚点」在类型上成立——一次漏传就是一次
 * 越过作用域的写，且不报错。写接口的「必填」必须在类型上落地。</p>
 */
@Data
public class TradeOrderReceiveDTO {

    /**
     * 顾客账号 id（= {@code mall_user.id}，数据权限锚点；**必填**，由端 BFF 从登录态取）
     */
    @NotNull(message = "顾客 id 不能为空")
    private Long customerId;
}
