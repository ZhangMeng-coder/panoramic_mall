package com.panoramic.admin.controller.stats;

import com.panoramic.admin.bff.AdminStatsBffService;
import com.panoramic.admin.dto.AdminStatsQueryDTO;
import com.panoramic.admin.vo.AdminStatsVO;
import com.panoramic.common.vo.RespData;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * admin 端 BFF · 平台首页数据看板（1 条）。
 *
 * <p>页面请求经网关 {@code /admin/stats/**} → 本控制器 → {@link AdminStatsBffService} →
 * 内部 Feign 调 store / customer-center / trade-center 三个域。只做聚合与包装（{@code RespData}），
 * 不持有任何域实体与表。</p>
 *
 * <p>⚠ <b>无 {@code @PreAuthorize}</b>，与 {@code /permissions/menus} 同类（登录后必得数据）：
 * 主页 {@code /home} **刻意不入权限表**（由前端写死置顶菜单），故其数据接口也必须是「登录后必得」
 * ——给本接口挂权限串，会让**没有该权限的管理员落到一个取不到数的首页**。
 * ⚠ 若将来要限制看板可见范围，两边要**同时**改（主页入权限表 + 本接口挂权限串），只改一边就是坏的主页。
 * 见 {@code docs/contracts/admin.md} 第三节。</p>
 */
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final AdminStatsBffService adminStatsBffService;

    /**
     * 首页看板八个指标（窗口缺省 = 本月；自定义窗口的起止都必填，非法即 400）
     */
    @GetMapping("/overview")
    public RespData<AdminStatsVO> overview(@Validated AdminStatsQueryDTO dto) {
        return RespData.success(adminStatsBffService.overview(dto));
    }
}
