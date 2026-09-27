package com.panoramic.contract.customer.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 顾客统计查询参数（customer-center 域内部接口与 admin BFF 同源共享）。
 * <p>用途：平台首页数据看板的「用户数量」与「新增用户折线」。⚠ 本域<b>第一条非「单顾客自助」的接口</b>——
 * 它数的是<b>全平台</b>顾客，不按 {@code customerId} 过滤（对比同域其余 9 条：要么以 {@code {customerId}}
 * 为路径标识、要么在 DTO 里带锚点）。</p>
 * <p>⚠ <b>只收显式起止、不收「窗口枚举」也不收「粒度」</b>：{@code 本月/上季/去年} 这类日历算术与
 * 日→月归并<b>只在发起调用的端 BFF</b>（admin）里发生，域侧一律按天出点。理由与判据见
 * cross-cutting 第 25 条：窗口解析若下沉到各域，同一套月/季/年算术会在每个域里各存一份而互相漂移。</p>
 * <p>⚠ 两个字段<b>可不填</b>：都不填时接口只回「累计快照」{@code totalCount}，折线 {@code newSeries}
 * 为空列表（快照与窗口是两个独立部件，见 {@link com.panoramic.contract.customer.vo.CustomerStatsVO}）。
 * 只填一个（缺 start 或缺 end）视为参数错误 → {@code code=400}，不猜另一个边界。</p>
 */
@Data
public class CustomerStatsQueryDTO {

    /**
     * 窗口起（含当天）。与 {@link #end} 同日即「只看今天」。
     * <p>⚠ ISO {@code yyyy-MM-dd}（{@code @DateTimeFormat} 显式钉死，不依赖两端默认格式是否一致）。</p>
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate start;

    /**
     * 窗口止（<b>含当天整天</b>，即 {@code create_time < end + 1 天 00:00}）。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate end;
}
