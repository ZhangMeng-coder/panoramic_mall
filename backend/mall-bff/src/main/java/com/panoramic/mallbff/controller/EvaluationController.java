package com.panoramic.mallbff.controller;

import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationStatVO;
import com.panoramic.mallbff.dto.MallEvaluationPageQueryDTO;
import com.panoramic.mallbff.dto.MallEvaluationSubmitDTO;
import com.panoramic.mallbff.service.EvaluationBffService;
import com.panoramic.mallbff.vo.MallEvaluationItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端商品评价接口（提交 / 分页 / 星级分布，共 3 条）。
 *
 * <p><b>形状</b>：本类只碰 {@link EvaluationBffService}，<b>不注入</b>任何 Feign 客户端；
 * 订单门禁、SKU 快照归组、昵称头像补齐都收在该 service 内。出参一律包 {@code RespData}
 * （含 {@code void} 的写路径）。</p>
 *
 * <p>⚠ <b>C 端没有回复入口</b>：回复只在商户端（{@code POST /evaluations/{id}/reply} 那条属 store-bff）。
 * 本端对评价只有「写自己的」与「读」。</p>
 *
 * <p>⚠ <b>顾客 id 只能取自 {@code UserContext}</b>（登录态），绝不从请求体 / 路径接收：
 * 域内不做任何鉴权（{@code customerId} 就是数据权限本身），BFF 是唯一授权点。
 * 三条路径<b>均需登录态</b>（登记在 {@code application.yml} 的「需登录态端点」注），
 * <b>不得</b>加进任何免鉴权白名单。</p>
 *
 * <p>⚠ <b>「订单已完成才能评价」是业务前置条件校验，不是鉴权</b>（门禁 = 业务门禁）：
 * 它落在 {@link EvaluationBffService#submit} 里经 trade-center 判，失败回同一句 <b>400</b>
 * 「订单不存在或尚未完成，无法评价」——不暴露订单是否存在。</p>
 *
 * <p><b>错误形状</b>：域侧业务 4xx 原样透传——重复评价 400「该商品已评价」、评分越界 400、
 * 评价内容超长 400；只有下游故障才降级为 500「评价暂不可用，请稍后重试」。</p>
 */
@RestController
@RequestMapping("/evaluations")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationBffService evaluationBffService;

    /**
     * 提交商品评价（粒度 = 商品 SPU；同单同一商品的多个 SKU 由服务端归组成一条）
     * <p>⚠ 入参<b>不含</b> {@code customerId}（登录态替代）与 {@code skuSnapshot}
     * （从订单明细归组而来）——理由见 {@link MallEvaluationSubmitDTO}。
     * 出参 {@code Void}：页面提交成功后重拉评价分页与星级分布。</p>
     */
    @PostMapping
    public RespData<Void> submit(@Valid @RequestBody MallEvaluationSubmitDTO dto) {
        evaluationBffService.submit(UserContext.getUserId(), dto);
        return RespData.success();
    }

    /**
     * 商品评价分页（固定时间倒序；每页条数由页面传，按商品取）
     * <p>⚠ 裸 {@code @Valid}：分页字段的约束落在 {@code BasePageVO} 的默认组
     * （理由见 {@code CatalogController#goods}）。</p>
     * <p>⚠ <b>没有星级筛选</b>：C 端要的是分布（见 {@link #stat}），星级筛选是商户端的入口。</p>
     */
    @PostMapping("/page")
    public RespData<PageResult<MallEvaluationItemVO>> page(@Valid @RequestBody MallEvaluationPageQueryDTO dto) {
        return RespData.success(evaluationBffService.page(dto));
    }

    /**
     * 商品评价星级分布（1~5 星各多少人 + 总条数，固定 5 行含 0 人的星级）
     * <p>⚠ 出参<b>直接是域类型</b> {@link StoreGoodsEvaluationStatVO}：无可裁剪 / 需补齐的字段，
     * 故不另造本端 VO（「同一份形状不造第二个出口」）。
     * ⚠ 路径里是 <b>{@code spuId}（资源标识）</b>而不是作用域——任何登录顾客都可看任一商品的分布。</p>
     */
    @GetMapping("/stat/{spuId}")
    public RespData<StoreGoodsEvaluationStatVO> stat(@PathVariable("spuId") Long spuId) {
        return RespData.success(evaluationBffService.stat(spuId));
    }
}
