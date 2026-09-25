package com.panoramic.store.controller;

import com.panoramic.contract.store.dto.StoreGoodsEvaluationOrderQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationReplyDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationStatQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationSubmitDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationPageItemVO;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationStatVO;
import com.panoramic.store.service.StoreGoodsEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品评价内部领域接口（store 域）。
 * <p>仅供端 BFF 经内部 Feign（{@code /internal/store/goods/evaluation/**}）调用，
 * 不对页面暴露公网路由；方法直接返回业务原类型（不包 RespData），错误经
 * {@code StoreDomainExceptionHandler} 以真实 HTTP 状态码传播。</p>
 * <p><b>作用域在哪</b>（见 store.md 第三节）：
 * <ul>
 *   <li>{@code submitEvaluation} —— 作用域是 {@code customerId}（评价人），<b>无 storeId</b>：
 *       店铺归属由域内按 {@code spuId} 反查，调用方指定不了；</li>
 *   <li>{@code replyEvaluation} —— 作用域是 {@code storeId}（只能回复本店评价），必填；</li>
 *   <li>三条读 —— <b>跨店通用</b>（{@code storeId} / {@code spuId} 可空，C 端传商品、商户端传店铺）。</li>
 * </ul>
 * ⚠ 这些 DTO 都<b>不是任何端的页面入参类型</b>（两端评价页面各有私有 DTO），故作用域必填直接落
 * <b>默认组</b> {@code @NotNull}，不挂 {@code StoreScopeGroup}（那个分档机制只为「域 DTO 同时是页面
 * 入参类型」而存在）。</p>
 * <p><b>域内不做鉴权、不判订单状态</b>：审核门禁与「订单已完成」都在端 BFF 前置，域侧只执行。</p>
 */
@RestController
@RequestMapping("/internal/store/goods/evaluation")
@RequiredArgsConstructor
public class EvaluationController {

    private final StoreGoodsEvaluationService storeGoodsEvaluationService;

    /**
     * 提交商品评价（一笔订单里的一个商品一条；重复提交 → 400「该商品已评价」）
     */
    @PostMapping
    public void submitEvaluation(@Validated @RequestBody StoreGoodsEvaluationSubmitDTO dto) {
        storeGoodsEvaluationService.submit(dto);
    }

    /**
     * 评价分页（时间倒序；跨店通用，按店铺 / 商品 / 星级任意组合筛选）。
     * <p>用 POST + body：{@code scores} 是集合（同 {@code /goods/cross-shop/spu/page} 口径）。</p>
     */
    @PostMapping("/page")
    public PageResult<StoreGoodsEvaluationPageItemVO> pageEvaluations(
            @Validated @RequestBody StoreGoodsEvaluationPageQueryDTO dto) {
        return storeGoodsEvaluationService.page(dto);
    }

    /**
     * 评价星级分布（固定 1~5 五行、无评价的星级补 0）。
     * <p>无集合字段，故走 GET + query（{@code /page} 与 {@code /stat} 是两个字面量路径，不冲突）。</p>
     */
    @GetMapping("/stat")
    public StoreGoodsEvaluationStatVO evaluationStat(@Validated StoreGoodsEvaluationStatQueryDTO query) {
        return storeGoodsEvaluationService.stat(query);
    }

    /**
     * 查某订单里<b>已评价过</b>的商品 SPU id 集合（订单详情页标「已评价 / 待评价」用）。
     * <p>订单号是路径变量（资源标识，不进 DTO）；顾客锚点走 DTO。未评价过 → 空列表。</p>
     */
    @GetMapping("/order/{orderNo}/spu-ids")
    public List<Long> listEvaluatedSpuIds(@PathVariable("orderNo") String orderNo,
                                         @Validated StoreGoodsEvaluationOrderQueryDTO query) {
        return storeGoodsEvaluationService.listEvaluatedSpuIds(orderNo, query);
    }

    /**
     * 商家回复评价（一条评价至多一条回复；已回复 → 400「该评价已回复」）
     */
    @PostMapping("/{id}/reply")
    public void replyEvaluation(@PathVariable("id") Long id,
                                @Validated @RequestBody StoreGoodsEvaluationReplyDTO dto) {
        storeGoodsEvaluationService.reply(id, dto);
    }
}
