package com.panoramic.mallbff.service;

import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.customer.api.CustomerCenterClient;
import com.panoramic.contract.customer.dto.CustomerProfileBatchQueryDTO;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端顾客资料编排（customer-center 域在本端的唯一落点）。
 * <p><b>为什么单独成类</b>：本仓库既有分层是「controller 不持 Feign 客户端、编排在 BFF service」
 * （同 {@link CatalogBffService}，地址侧的 {@code CustomerAddressBffService} 同形）；且「域取不到资料
 * 要静默留空」是 customer-center 的<b>失败语义</b>，不该由 {@code AuthService} 去懂。
 * 资料的<b>读</b>（供 {@code /auth/me}）与<b>写</b>（供 {@code PUT /profile} 与注册播种）都收在这里，
 * 两条路径的降级语义在同一个文件里对照着看，不会各写一份。</p>
 *
 * <p>⚠ <b>读写两条路径的降级语义刻意相反</b>（见 docs/contracts/cross-cutting.md 第 13 条）：</p>
 * <ul>
 *   <li>{@link #loadProfile}：<b>读增强</b>，取不到就静默留空（返回 {@code null}），
 *       <b>绝不阻断</b> {@code /auth/me} 与登录 —— 故它<b>不</b>走 {@link BffFeignCall}
 *       （后者的语义是降级成 500 文案抛出去），与 {@code CatalogBffService} 对分类树的处理同类。</li>
 *   <li>{@link #saveProfile}：<b>写操作不可降级</b> —— 调用方（{@code PUT /profile} / 注册）拿到
 *       降级异常就必须整体失败（注册尤其如此：账号已插入而资料没写，用户重试会撞「手机号已注册」）。</li>
 * </ul>
 *
 * <p>⚠ 写口径是<b>整份覆盖</b>、不是增量更新：域侧对四列<b>无条件写入</b>（未传即写 {@code NULL}），
 * 故本层<b>原样透传</b> {@link CustomerProfileSaveDTO}、<b>不做任何 null 过滤</b> ——
 * 两边语义必须同向，否则读代码的人会把「覆盖」判成「合并」（详见该 DTO 的类注释与
 * docs/contracts/mall-bff.md 的「资料写口径」）。</p>
 *
 * <p>三件事收在这里：<b>读</b>（{@link #loadProfile} 单条 / {@link #loadProfiles} 批量）、
 * <b>写</b>（{@link #saveProfile}）、以及<b>默认昵称规则本体</b>
 * （{@link #withDefaultNickname}，「用户」+ 手机号后 4 位——只在写入侧一处实现，
 * 见 docs/contracts/mall-bff.md 的「默认昵称」）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileBffService {

    /** customer-center 熔断/连接异常降级提示（仅写路径用；读路径静默留空、不走降级文案） */
    private static final String DOWN_MSG = "顾客资料暂不可用，请稍后重试";

    /**
     * 默认昵称前缀（「用户」+ 手机号后 4 位这条规则的<b>规则本体</b>在本类，
     * 见 {@link #withDefaultNickname}；读取侧的占位名引用本常量，免得同一个字面量出现两次）
     */
    public static final String NICKNAME_PREFIX = "用户";

    /** 默认昵称里保留的手机号位数 */
    private static final int PHONE_TAIL_LENGTH = 4;

    private final CustomerCenterClient customerCenterClient;

    /**
     * 读资料（供 {@code /auth/me} 的读增强）。
     * <p>⚠ 取不到就<b>降级返回 null</b>，绝不阻断登录/鉴权。故<b>不</b>走 {@link BffFeignCall} ——
     * 它的语义是降级成 500 文案，这里要的是「静默留空」。与 {@code CatalogBffService} 对分类树的处理同类。</p>
     * <p>域侧无记录时返回「仅含 id 的空 VO」（不返回 null、不抛 404），故非 null 返回值里的资料字段
     * 仍可能全为空；昵称为空的兜底<b>不在这里</b>，在 {@code AuthService#toCurrentUser} 一处（spec §6.2）。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @return 顾客资料；域不可用等异常时为 null
     */
    public CustomerProfileVO loadProfile(Long customerId) {
        try {
            return customerCenterClient.getProfile(customerId);
        } catch (Exception e) {
            log.warn("顾客资料获取失败，降级为空（customerId={}）", customerId, e);
            return null;
        }
    }

    /**
     * <b>批量</b>读资料（评价列表一次补齐昵称 / 头像用）。
     * <p>⚠ 逐条调 {@link #loadProfile} 是 N+1，禁止：一页 10 条评价就是 10 次跨服务往返。
     * 与 {@link #loadProfile} 同款降级语义（读增强，取不到返回<b>空表</b>、绝不阻断主流程），
     * 故同样<b>不走</b> {@link BffFeignCall}。</p>
     * <p>⚠ 域侧出参口径与单条不同：<b>查不到的 id 直接跳过</b>（不补「仅含 id 的空 VO」），
     * 故调用方按「该 id 不在 map 里 = 无资料」处理，并自己兜底展示名。</p>
     *
     * @param customerIds 顾客账号 id 集合（= mall_user.id；空集直接返回空表，不调域）
     * @return 命中的顾客资料（按 id 索引，可能不完整 / 为空）；域不可用时为空表
     */
    public Map<Long, CustomerProfileVO> loadProfiles(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            CustomerProfileBatchQueryDTO query = new CustomerProfileBatchQueryDTO();
            query.setCustomerIds(new ArrayList<>(customerIds));
            List<CustomerProfileVO> profiles = customerCenterClient.listProfilesByIds(query);
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
            log.warn("顾客资料批量获取失败，降级为空（customerIds={}）", customerIds, e);
            return Collections.emptyMap();
        }
    }

    /**
     * <b>默认昵称规则本体</b>（「用户」+ 手机号后 4 位）——全模块<b>唯一</b>实现处。
     *
     * <p>⚠ <b>只在写入侧用，落成 {@code customer_profile.nickname} 的真数据</b>（不是读取侧兜底）：
     * 两个写入侧入口都调它——① 注册成功时（{@code AuthService#register}）；
     * ② {@code PUT /profile} 昵称留空时（{@code ProfileController#save}，否则「清空昵称」
     * 会重新制造无昵称用户）。</p>
     * <p>⚠ <b>读取侧不拼后 4 位</b>：那需要手机号，而手机号只在 {@code mall_user}（本端独有），
     * 商户端拿不到——两套算法必然漂移。评价区拿不到资料时用的是纯占位
     * （{@code EvaluationBffService} 的 {@code PLACEHOLDER_NICKNAME}）。</p>
     * <p>⚠ 存量空昵称<b>不回填</b>：上线前的老顾客走读取侧的占位名。</p>
     *
     * @param nickname 页面 / 注册传入的昵称（可空 / 空白）
     * @param phone    手机号（= 登录账号；注册来自入参、改资料来自登录态快照）
     * @return 非空的昵称：传了就用传的，没传则 {@code 用户 + 手机号后 4 位}
     */
    public static String withDefaultNickname(String nickname, String phone) {
        if (StringUtils.hasText(nickname)) {
            return nickname.trim();
        }
        String tail = phone == null ? "" : phone.trim();
        if (tail.length() > PHONE_TAIL_LENGTH) {
            // 取后 4 位：手机号是 11 位，理论上不会更短；更短时原样用，不补位、不截空
            tail = tail.substring(tail.length() - PHONE_TAIL_LENGTH);
        }
        return NICKNAME_PREFIX + tail;
    }

    /**
     * 写资料。⚠ <b>不可降级</b>：调用方（{@code PUT /profile} / 注册）拿到降级异常就必须整体失败。
     * <p>走 {@link BffFeignCall}：400/403/404 原样透传（域内业务校验失败照实回给页面），
     * 其余（熔断/连接/序列化）降级为抛出的 500 文案。</p>
     * <p>⚠ 四列<b>全量透传</b>：本层不做「非 null 才 set」的合并语义，域侧写的是整份覆盖。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，取自登录态）
     * @param dto        资料字段（整份，未传字段会被写成 NULL）
     */
    public void saveProfile(Long customerId, CustomerProfileSaveDTO dto) {
        BffFeignCall.call("customer-center", DOWN_MSG,
                () -> {
                    customerCenterClient.saveProfile(customerId, dto);
                    return null;
                });
    }
}
