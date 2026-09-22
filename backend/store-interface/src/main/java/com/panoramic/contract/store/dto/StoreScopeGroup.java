package com.panoramic.contract.store.dto;

/**
 * 作用域校验组：DTO 上的 {@code storeId}（数据作用域锚点）<b>只在域入口必填</b>。
 *
 * <p>为什么不能用默认组：本切片的作用域字段是**后加的**（cross-cutting 第 22/23 条：锚点从位置裸参
 * 移入入参 DTO），而这些 DTO 同时是 store-bff 的**页面入参类型**——页面按第 22 条**不提供**
 * 作用域字段（它由本层从登录态无条件覆盖）。页面控制器对同一个类跑 {@code @Valid}/{@code @Validated}
 * （默认组），若 {@code storeId} 的 {@code @NotNull} 落在默认组，页面每一次保存/分页都会被 400 拒掉
 * （「店铺ID不能为空」），而页面根本无从提供该值。</p>
 *
 * <p>于是分成两处各校验各的（同一份 DTO，两种调用面）：
 * <ul>
 *   <li><b>域入口</b>（store 域 controller）：{@code @Validated({Default.class, StoreScopeGroup.class})}
 *       ——默认约束照旧 + 作用域必填，缺 {@code storeId} 的写入请求得到 <b>HTTP 400</b>；</li>
 *   <li><b>页面入口</b>（端 BFF controller）：{@code @Valid}/{@code @Validated}（默认组）
 *       —— 作用域约束不参与，页面不必也不得提供该字段。</li>
 * </ul>
 * ⚠ 新增域方法时别漏了域侧那半个组：只写 {@code @Validated} 会让作用域变成「可空」，
 * 缺参一路走到服务层（NPE 或静默写错数据）。</p>
 */
public interface StoreScopeGroup {
}
