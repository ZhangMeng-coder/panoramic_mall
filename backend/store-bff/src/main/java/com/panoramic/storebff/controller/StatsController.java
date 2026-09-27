package com.panoramic.storebff.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.storebff.bff.StoreStatsBffService;
import com.panoramic.storebff.dto.StoreStatsQueryDTO;
import com.panoramic.storebff.vo.StoreStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺端 BFF · 店主端首页数据看板（<b>1 条</b>）。
 * <p>页面请求经网关 {@code /store/stats/**} → 本控制器 → 内部 Feign 调 store 域（商品计数 + 评价分布）
 * 与 trade-center（订单四口径）；编排见 {@link StoreStatsBffService}。
 * 只做聚合与包装（{@code RespData}），不持有任何域实体与表。</p>
 *
 * <p>⚠ <b>一个接口承载 8 项</b>（3 快照 + 5 窗口），与平台看板 {@code /admin/stats/overview} 同形：
 * 首页一次加载要么全有要么全无，拆开只会换来多次往返与多份 loading 态。</p>
 *
 * <p>⚠ <b>作用域取自登录态</b>：本类<b>不接收</b> {@code storeId}（页面入参里也没有该字段），
 * 由 {@link StoreStatsBffService} 从 {@code UserContext} 取当前店主账号 id 后无条件写进三个域入参 DTO
 * ——域内不做任何鉴权，这个 id 就是数据权限本身（cross-cutting 第 22 条）。</p>
 *
 * <p>⚠ <b>无 {@code @PreAuthorize}</b>：店铺端不接 RBAC（登录态是唯一门槛），店主对自己店的经营数据
 * 全权限。⚠ 也<b>不套</b>本模块 {@code /goods/**} 的店铺审核门禁（R9）：首页不在「开店后业务入口」
 * 那组页面里，未开店 / 未过审同样打得开——故本接口<b>容忍空店</b>：各计数回 0、折线回一串 0
 * （零填充的完整桶序列，<b>不是</b>空数组），<b>不是</b> 404、<b>不是</b> 403、<b>不是</b>降级 500。</p>
 *
 * <p><b>错误形状</b>：窗口不合法（自定义缺一端 / 起止倒挂）→ 400 中文提示、登录态失效 → 400；
 * 只有下游故障才降级为 500 {@code 店铺服务暂不可用，请稍后重试} / {@code 订单服务暂不可用，请稍后重试}
 * （store 域与 trade-center 各一句，见 {@code BffFeignCall}）。</p>
 *
 * <p>⚠ <b>各指标口径属业务规则</b>（谁的窗口、评价分布按不按窗口、库存异常怎么判），
 * 见 {@code backend/store-bff/README.md} 与各域 README，本类只登记接口与形状。</p>
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
@Validated
public class StatsController {

    private final StoreStatsBffService storeStatsBffService;

    /**
     * 本店首页看板（3 个快照计数 + 5 个窗口指标）
     * <p>窗口枚举 + 自定义起止由 {@code common} 的 {@code StatsWindows} 解析；粒度同源，
     * 页面照 {@code grain} 格式化 x 轴即可。</p>
     */
    @GetMapping("/overview")
    public RespData<StoreStatsVO> overview(@Validated StoreStatsQueryDTO dto) {
        return RespData.success(storeStatsBffService.overview(dto));
    }
}
