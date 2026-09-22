package com.panoramic.trade.support;

import com.panoramic.common.exception.ServiceException;

/**
 * 写路径的**作用域非空**护栏（订单 4 个写 + 购物车 5 个写共用这一处）。
 *
 * <p>为什么需要它，即使契约层的入参 DTO 上已经写了 {@code @NotNull}：<b>那道校验只覆盖 MVC 边界</b>。
 * 任何绕过 MVC 的调用（内部 Feign 直连、单测、将来可能的批处理 / 定时任务）都不经过 {@code @Validated}，
 * 于是作用域可以是 {@code null}。</p>
 *
 * <p>⚠ 而 {@code null} 在域内的含义是「**不限定**」（cross-cutting 第 22 条：读侧要支持管理端全量视角），
 * 于是写路径会退化成「按单号取**任意一笔**并改它」——不报错、只是把别人的单改了。
 * 落到数据库的 {@code NOT NULL} 更糟：那会以 500 出去（调用方按 cross-cutting 第 13 条把它算进熔断失败率），
 * 而「调用方漏传参数」应当是 400。</p>
 *
 * <p>⚠ <b>这是入参不变量，不是鉴权</b>：本类只判「参数在不在」，不判「这笔单是不是你的」
 * ——后者是身份判断，域内一律不做（防线在端 BFF）。故文案只说**缺少哪个参数**，
 * 绝不说「这单不是你的」之类的话术。</p>
 */
public final class ScopeGuard {

    private ScopeGuard() {
    }

    /**
     * 断言写操作的作用域已带（{@code null} 即 400）
     *
     * @param scope 作用域取值（{@code customerId} / {@code storeId}）
     * @param label 缺失时回给调用方的字段中文名（如「顾客 id」）
     * @throws ServiceException 作用域为 {@code null}（HTTP 400）
     */
    public static void require(Long scope, String label) {
        if (scope == null) {
            throw new ServiceException(400, "缺少" + label + "：写操作必须携带数据作用域");
        }
    }
}
