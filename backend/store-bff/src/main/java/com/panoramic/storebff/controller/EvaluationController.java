package com.panoramic.storebff.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.storebff.bff.StoreEvaluationBffService;
import com.panoramic.storebff.dto.StoreEvaluationPageQueryDTO;
import com.panoramic.storebff.dto.StoreEvaluationReplyDTO;
import com.panoramic.storebff.vo.StoreEvaluationItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 店铺端 BFF · 商户侧评价接口（列表 / 回复，共 2 条）。
 * <p>页面请求经网关 {@code /store/evaluations/**} → 本控制器 → 内部 Feign 调 store 域；
 * 评价人的昵称 / 头像另经 customer-center 批量补（见 {@link StoreEvaluationBffService}）。
 * 只做聚合与包装（{@code RespData}），不持有 store 域的任何实体与表。</p>
 *
 * <p>⚠ <b>独立菜单页「评价」</b>（与「订单管理」平级），<b>不是</b>订单详情里的子区块。</p>
 *
 * <p>⚠ <b>作用域取自登录态</b>：本类<b>不接收</b> {@code storeId}（页面入参里也没有该字段），
 * 由 {@link StoreEvaluationBffService} 从 {@code UserContext} 取当前店主账号 id 后无条件写进域入参 DTO
 * ——域内不做任何鉴权，这个 id 就是数据权限本身（cross-cutting 第 22 条）。</p>
 *
 * <p>⚠ <b>无 {@code @PreAuthorize}</b>：店铺端不接 RBAC（登录态是唯一门槛），店主对自己店的评价
 * 全权限。⚠ 也<b>不套</b>本模块 {@code /goods/**} 的店铺审核门禁（R9）：评价是既有事实，
 * 未过审的店同样要看得到（与订单全状态可见同款）。</p>
 *
 * <p><b>错误形状</b>：下游业务 4xx 原样透传——回复已回复过的评价 400「该评价已回复」、
 * 评价不存在或不属本店 404 如实回页面；只有下游故障才降级为 500
 * {@code 评价服务暂不可用，请稍后重试}。</p>
 */
@RestController
@RequestMapping("/evaluations")
@RequiredArgsConstructor
@Validated
public class EvaluationController {

    private final StoreEvaluationBffService storeEvaluationBffService;

    /**
     * 本店评价分页（时间倒序；可按商品筛选、按星级筛选——<b>单选</b>）
     */
    @GetMapping("/page")
    public RespData<PageResult<StoreEvaluationItemVO>> page(@Validated StoreEvaluationPageQueryDTO dto) {
        return RespData.success(storeEvaluationBffService.page(dto));
    }

    /**
     * 回复评价（一条评价至多一个回复；重复回复 → 400 原样透传）
     */
    @PostMapping("/{id}/reply")
    public RespData<Void> reply(@PathVariable("id") Long id,
                                @Valid @RequestBody StoreEvaluationReplyDTO dto) {
        storeEvaluationBffService.reply(id, dto);
        return RespData.success();
    }
}
