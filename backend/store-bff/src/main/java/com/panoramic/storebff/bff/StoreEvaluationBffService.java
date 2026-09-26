package com.panoramic.storebff.bff;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.common.feign.DomainResp;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.dto.CustomerProfileBatchQueryDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationPageQueryDTO;
import com.panoramic.contract.store.dto.StoreGoodsEvaluationReplyDTO;
import com.panoramic.contract.store.vo.PageResult;
import com.panoramic.contract.store.vo.StoreGoodsEvaluationPageItemVO;
import com.panoramic.storebff.dto.StoreEvaluationPageQueryDTO;
import com.panoramic.storebff.dto.StoreEvaluationReplyDTO;
import com.panoramic.storebff.vo.StoreEvaluationItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 店铺端 BFF · 商户侧评价编排（本店评价列表 / 商家回复）。
 * <p>只做页面编排：评价数据（含商品与店铺的评分冗余列）<b>全在 store 域</b>
 * （{@code store_goods_evaluation}），本层不落表、不复制实体，经 {@link StoreClient} 调用。</p>
 *
 * <p><b>锚点一律取自登录态</b>（{@link #currentStoreId()}，店主账号 id == store_id），<b>无条件</b>
 * 写进域入参 DTO；页面入参里<b>不含</b> {@code storeId} 字段（{@code StoreEvaluationPageQueryDTO} /
 * {@code StoreEvaluationReplyDTO}）——页面能传来的锚点等于把数据权限交给页面（cross-cutting 第 22 条）。
 * 回复的归属校验（只能回本店评价）由域侧按 {@code id + storeId} 做，本层<b>不重判</b>。</p>
 *
 * <p><b>本层补的是域做不到的两件事</b>（同 mall-bff 的 {@code EvaluationBffService}，但那边面向 C 端）：</p>
 * <ul>
 *   <li><b>评价人展示名 / 头像</b>：域出参只有 {@code customerId}，资料在 customer-center。
 *       一页一次 {@code listProfilesByIds} <b>批量</b>取（<b>禁止</b>逐条 {@code getProfile}：一页 10 条
 *       就是 10 次跨服务往返）。</li>
 *   <li><b>商品名兜底</b>：域侧按 {@code spuId} 批量反查当前商品名，商品已软删时下发 {@code null}
 *       （域侧不做文案——那是各端的展示决定），本层填「商品已删除」。</li>
 * </ul>
 * <p>⚠ 星级筛选在<b>页面</b>是单选、在<b>域侧</b>是集合（{@code scores}），由本层转单元素集合
 * （理由见 {@code StoreEvaluationPageQueryDTO} 类注释）。</p>
 *
 * <p>⚠ 评价文字 / 回复文字<b>纯文本</b>：本层不接 {@code HtmlSanitizer}（那条口径的前提是
 * 「店主自由录入 HTML」，评论没有这个前提），前端按插值渲染、不许 {@code v-html}。</p>
 *
 * <p>下游业务异常（400 业务 / 403 / 404）沿 cause 链剥出后原样透传——回复重复时的
 * 域侧 400「该评价已回复」照实回页面；其余（熔断 / 连接 / 序列化）降级为友好提示，
 * 该逻辑已抽到 common 的 {@link BffFeignCall}，本类只传降级文案。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoreEvaluationBffService {

    /** store 域熔断 / 连接异常降级提示 */
    private static final String EVALUATION_DEGRADE_MSG = "评价服务暂不可用，请稍后重试";

    /** 商品已软删时的兜底商品名（域侧下发 {@code null} 时由本层填，见 {@code StoreEvaluationItemVO#spuName}） */
    private static final String SPU_NAME_FALLBACK = "商品已删除";

    /**
     * 评价人资料取不到时的占位展示名。
     * <p>⚠ <b>不是</b> mall-bff 那条「用户 + 手机号后 4 位」规则：那需要手机号，而手机号只存在于 C 端
     * 账号表（{@code mall_user}），商户端拿不到——两套算法必然漂移。故这边只用纯占位，
     * 规则本体（写入侧）仍只在 mall-bff 一处（{@code CustomerProfileBffService#withDefaultNickname}）。</p>
     */
    private static final String PLACEHOLDER_NICKNAME = "用户";

    private final StoreClient storeClient;
    private final CustomerCenterClient customerCenterClient;

    // ---- 评价（数据在 store 域，作用域由本层从登录态无条件写入）----

    /**
     * 本店评价分页（时间倒序），可按商品与星级收窄。
     * <p>页面 DTO 与域 DTO 是两份类型：本层组装域入参，把 {@code storeId} 无条件写成登录态
     * （店主侧只有「本店评价」一个视角，页面无权选择看哪家店）。</p>
     *
     * @param dto 页面筛选与分页条件（商品 <b>可选</b> / 星级 <b>单选</b>）
     * @return 分页结果（每行带商品名与评价人展示名）
     */
    public PageResult<StoreEvaluationItemVO> page(StoreEvaluationPageQueryDTO dto) {
        StoreGoodsEvaluationPageQueryDTO query = new StoreGoodsEvaluationPageQueryDTO();
        query.setPageNum(dto.getPageNum());
        query.setPageSize(dto.getPageSize());
        query.setStoreId(currentStoreId());
        query.setSpuId(dto.getSpuId());
        // 页面单选 → 域侧集合；不选星级时整个字段留空（域侧「空 = 不筛」，不是空集过滤）
        query.setScores(dto.getScore() == null ? null : List.of(dto.getScore()));

        PageResult<StoreGoodsEvaluationPageItemVO> raw = call(() -> storeClient.pageEvaluations(query));

        List<StoreGoodsEvaluationPageItemVO> records =
                raw.getRecords() == null ? List.of() : raw.getRecords();
        Map<Long, CustomerProfileVO> profiles = loadProfiles(customerIdsOf(records));

        PageResult<StoreEvaluationItemVO> result = new PageResult<>();
        result.setTotal(raw.getTotal());
        result.setRecords(toItems(records, profiles));
        return result;
    }

    /**
     * 回复评价（一条评价至多一个回复；回复后不可改、不可删）。
     * <p>⚠ 判据在域侧（以「回复列为空」为条件更新，影响行数是唯一依据），本层<b>不重判</b>：
     * 重复回复 → 域侧 400「该评价已回复」经 {@link BffFeignCall} 原样透传给页面；
     * 评价不存在或不属本店 → 域侧 404。</p>
     *
     * @param id  评价 id（路径变量）
     * @param dto 回复内容（页面入参，不含锚点）
     */
    public void reply(Long id, StoreEvaluationReplyDTO dto) {
        StoreGoodsEvaluationReplyDTO payload = new StoreGoodsEvaluationReplyDTO();
        payload.setStoreId(currentStoreId());
        payload.setReplyContent(dto.getReplyContent());
        call(() -> storeClient.replyEvaluation(id, payload));
    }

    // ---- 编排辅助 ----

    /**
     * 逐字段映射成页面类型（<b>不用 {@code BeanUtils.copyProperties}</b>，同 {@code StoreGoodsSpuDetailBffVO}）。
     * <p>两处本层的展示决定：商品名兜底（域侧软删时为 {@code null}）、评价人占位名（资料取不到时）。</p>
     */
    private List<StoreEvaluationItemVO> toItems(List<StoreGoodsEvaluationPageItemVO> records,
                                                Map<Long, CustomerProfileVO> profiles) {
        List<StoreEvaluationItemVO> items = new ArrayList<>(records.size());
        for (StoreGoodsEvaluationPageItemVO src : records) {
            StoreEvaluationItemVO vo = new StoreEvaluationItemVO();
            vo.setId(src.getId());
            vo.setSpuId(src.getSpuId());
            vo.setSpuName(StringUtils.hasText(src.getSpuName()) ? src.getSpuName() : SPU_NAME_FALLBACK);
            vo.setSkuSnapshot(src.getSkuSnapshot());
            vo.setScore(src.getScore());
            vo.setContent(src.getContent());
            vo.setCreateTime(src.getCreateTime());
            vo.setReplyContent(src.getReplyContent());
            vo.setReplyTime(src.getReplyTime());

            CustomerProfileVO profile = src.getCustomerId() == null ? null : profiles.get(src.getCustomerId());
            String nickname = profile == null ? null : profile.getNickname();
            vo.setNickname(StringUtils.hasText(nickname) ? nickname : PLACEHOLDER_NICKNAME);
            // 头像空值不兜底（「无头像」就是没有，前端按 null 不渲染 <el-avatar> 的 src）
            vo.setAvatar(profile == null || !StringUtils.hasText(profile.getAvatar())
                    ? null : profile.getAvatar());
            items.add(vo);
        }
        return items;
    }

    /**
     * 收集本页评价人 id（去重；未登录态缺失的 {@code customerId} 跳过）
     */
    private Set<Long> customerIdsOf(List<StoreGoodsEvaluationPageItemVO> records) {
        Set<Long> ids = new LinkedHashSet<>();
        for (StoreGoodsEvaluationPageItemVO item : records) {
            if (item != null && item.getCustomerId() != null) {
                ids.add(item.getCustomerId());
            }
        }
        return ids;
    }

    /**
     * <b>批量</b>取评价人资料（昵称 / 头像）。
     * <p>⚠ <b>读增强，静默降级</b>：取不到返回<b>空表</b>、绝不拖垮评价列表（评价本身是主体，昵称只是装饰），
     * 故<b>不走</b> {@link BffFeignCall}（它的语义是降级成 500 文案抛出去）——与 mall-bff 的
     * {@code CustomerProfileBffService#loadProfiles} 同款。占位名由 {@link #toItems} 统一填。</p>
     *
     * @param customerIds 本页评价人 id 集合（空集直接返回空表，不调域）
     * @return 命中的资料（按 id 索引，可能不完整 / 为空）；域不可用时为空表
     */
    private Map<Long, CustomerProfileVO> loadProfiles(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            CustomerProfileBatchQueryDTO query = new CustomerProfileBatchQueryDTO();
            query.setCustomerIds(new ArrayList<>(customerIds));
            List<CustomerProfileVO> profiles = DomainResp.unwrap(customerCenterClient.listProfilesByIds(query));
            if (profiles == null || profiles.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<Long, CustomerProfileVO> byId = new HashMap<>();
            for (CustomerProfileVO profile : profiles) {
                if (profile != null && profile.getId() != null) {
                    byId.put(profile.getId(), profile);
                }
            }
            return byId;
        } catch (Exception e) {
            log.warn("评价人资料批量获取失败，本次按占位名下发（评价列表照常返回，增强读静默降级）: customerIds={}",
                    customerIds, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 取当前登录店主账号 id（== store_id），登录态缺失时拒绝
     */
    private Long currentStoreId() {
        LoginUser loginUser = UserContext.getLoginUser();
        if (loginUser == null || loginUser.getId() == null) {
            throw new ServiceException("登录已失效，请重新登录");
        }
        return loginUser.getId();
    }

    /**
     * 调 store 域的统一编排执行（异常剥壳与降级见 {@link BffFeignCall}）
     */
    private <T> T call(Supplier<RespData<T>> action) {
        return BffFeignCall.call("store", EVALUATION_DEGRADE_MSG, action);
    }
}
