package com.panoramic.mallbff.service;

import com.panoramic.common.auth.JwtService;
import com.panoramic.common.auth.LoginUserCacheService;
import com.panoramic.common.enums.ServiceExceptionEnums;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.security.LoginUser;
import com.panoramic.common.util.UserContext;
import com.panoramic.contract.customer.dto.CustomerProfileSaveDTO;
import com.panoramic.contract.customer.vo.CustomerProfileVO;
import com.panoramic.mallbff.dto.LoginDTO;
import com.panoramic.mallbff.dto.RegisterDTO;
import com.panoramic.mallbff.dto.SmsCodeDTO;
import com.panoramic.mallbff.entity.MallUser;
import com.panoramic.mallbff.vo.CurrentUserVO;
import com.panoramic.mallbff.vo.LoginResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * C 端顾客认证服务（手机号 + 短信验证码，注册即登录；账号栈归商城前台 BFF）
 * <p>顾客独立账号（mall_user），userType=user 与平台管理员 admin、店主 store 经 Redis 键与
 * JWT type claim 隔离。C 端无 RBAC 角色/权限维度，LoginUser 快照 roleIds/perms 为空集合。</p>
 * <p><b>昵称与资料归 customer-center</b>：{@code mall_user} 不再持 {@code nickname}，
 * 昵称/头像/性别/生日统一经 {@link CustomerProfileBffService} 读写——注册时播种，
 * 登录返回与 {@code /auth/me} 时读取（读失败静默留空，不阻断登录）。</p>
 * <p>⚠ **短信通道为模拟实现**：取码只打日志、不发真实短信、不落库、不落 Redis；校验一律与
 * {@code panoramic.mall.sms-fixed-code}（默认 888888）比对。接真实短信服务时，只需替换
 * {@link #sendSmsCode} 与 {@link #assertCode} 两处。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final MallUserService mallUserService;
    private final LoginUserCacheService loginUserCacheService;
    private final JwtService jwtService;
    /** 顾客资料编排（customer-center）；⚠ 注入的是 BFF service **不是** CustomerCenterClient——
     *  降级判断（读留空 / 写不可降级）在 service 一侧，见 CustomerProfileBffService */
    private final CustomerProfileBffService customerProfileBffService;

    /**
     * 模拟短信的固定验证码（Nacos/本地配置；缺省 888888）
     */
    @Value("${panoramic.mall.sms-fixed-code:888888}")
    private String smsFixedCode;

    /**
     * 「获取验证码」：只做格式校验与日志留痕，**不真发短信、不落库、不落 Redis**
     * <p>手机号格式由 {@link SmsCodeDTO} 的 {@code @Pattern} 在前置校验完成，此处不再重复判断；
     * 也不校验手机号是否已注册——注册页与登录页共用本接口，两侧都要求能取到码。</p>
     *
     * @param dto 手机号
     */
    public void sendSmsCode(SmsCodeDTO dto) {
        log.info("[模拟短信] 手机号 {} 的验证码为:{}", dto.getPhone(), smsFixedCode);
    }

    /**
     * 注册（注册即登录）：验码 → 手机号查重 → 建账号 → 播种顾客资料 → 下发登录态
     * <p><b>为什么加 {@code @Transactional}</b>（本方法加注解的唯一理由，且<b>只有一个方向成立</b>）：
     * 资料写走 {@code CustomerProfileBffService#saveProfile}，那是<b>不可降级</b>的写路径，
     * 下游故障会抛出降级异常。此时若账号已插入，用户重试会撞「手机号已注册」、账号被永久锁死；
     * 加事务后<b>域写失败 → 账号回滚 → 重试可用</b>。该注解确实生效：本方法只被
     * {@code AuthController} 调用（无自调用绕代理），{@code AuthService} 是类而非接口，
     * 故走 CGLIB 代理。</p>
     * <p>⚠ <b>反方向不成立，不要读成「同生同死」</b>：域侧 {@code customer_profile} 有它<b>自己的</b>事务，
     * 在 {@code saveProfile} 返回时就<b>已提交</b>。此后本地事务若失败（{@code issueLogin} 里的
     * Redis / JWT 环节抛异常，或域其实已提交而 BFF 只看到超时），回滚掉的只有 {@code mall_user}，
     * 那一行资料<b>不参与回滚</b>（跨服务、无分布式事务）→ 留下<b>孤儿资料行</b>
     * （{@code customer_profile.id} 指向一个不存在的账号 id）。<b>这是已知取舍，不是「不可能发生」</b>：
     * 账号侧可自愈（重试即可），孤儿资料行对顾客不可见、对现有读路径也无害。</p>
     * <p>⚠ <b>代价（如实记）</b>：事务内串了<b>两次</b> Feign —— {@code saveProfile} 一次，
     * {@code issueLogin → toCurrentUser → loadProfile} 再一次；按 {@code feign-circuitbreaker.yml}
     * 的 TimeLimiter 10s，最坏情形<b>持有一个 DB 连接约 20s</b>。并发注册撞上域变慢时可能占满连接池
     * （未配 {@code maximum-pool-size}，即 Hikari 默认 10），此后<b>登录</b>（{@code getByPhone} 要连接）
     * 会排队——爆炸半径限于本端 auth 路径。</p>
     * <p>⚠ <b>为什么不改成 best-effort / afterCommit</b>（即「账号先落库、资料失败只记日志」）：
     * spec §6.2 明确要求写资料「<b>不可降级</b>：调了却写失败 → 注册整体失败（不能『注册成功但昵称丢了』）」
     * ——改它属<b>设计决策</b>，归用户，不在实现侧擅自处理；故此处不引入事务同步等新机制。</p>
     *
     * @param dto 注册参数
     * @return token + 当前用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO register(RegisterDTO dto) {
        String phone = dto.getPhone().trim();
        assertCode(dto.getCode());
        if (mallUserService.existsByPhone(phone)) {
            throw new ServiceException("手机号已注册");
        }
        MallUser user = new MallUser();
        user.setPhone(phone);
        // ⚠ 不再写 user.setNickname(...)：nickname 已从 mall_user 迁到 customer-center 的 customer_profile
        //    （列已删、实体字段已摘），昵称在这里经 saveProfile 播种，读路径见 toCurrentUser。
        user.setStatus(1);
        mallUserService.save(user);

        // 昵称非空才调域、才建资料行（spec §6.2）：空昵称没有信息量，
        // 建一行空资料只会让「资料是否存在」多出一种无意义状态
        if (StringUtils.hasText(dto.getNickname())) {
            CustomerProfileSaveDTO profile = new CustomerProfileSaveDTO();
            profile.setNickname(dto.getNickname().trim());
            // ⚠ 写操作不可降级：saveProfile 内部走 BffFeignCall，失败会抛出降级异常 → 整个注册回滚
            customerProfileBffService.saveProfile(user.getId(), profile);
        }
        return issueLogin(user);
    }

    /**
     * 登录：验码 → 按手机号查账号 → 校验状态 → 写顾客登录上下文到 Redis → 签发 type=user 的 JWT
     *
     * @param dto 登录参数
     * @return token + 当前用户信息
     */
    public LoginResultVO login(LoginDTO dto) {
        String phone = dto.getPhone().trim();
        assertCode(dto.getCode());
        MallUser user = mallUserService.getByPhone(phone);
        if (user == null) {
            throw new ServiceException("手机号未注册");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new ServiceException(ServiceExceptionEnums.USER_DISABLED);
        }
        return issueLogin(user);
    }

    /**
     * 登出：删除 Redis 中的顾客登录上下文，网关/服务下一请求即回 401
     *
     * @param userType 用户类型（user）
     * @param userId   顾客账号ID
     */
    public void logout(String userType, Long userId) {
        loginUserCacheService.delete(userType, userId);
    }

    /**
     * 当前登录顾客信息（/auth/me，刷新页面时拉取）
     *
     * @return 顾客信息
     */
    public CurrentUserVO currentUser() {
        return toCurrentUser(UserContext.getLoginUser());
    }

    /**
     * 校验短信验证码（模拟通道：与固定码比对）
     *
     * @param code 用户提交的验证码
     */
    private void assertCode(String code) {
        if (!StringUtils.hasText(code) || !smsFixedCode.equals(code.trim())) {
            throw new ServiceException("验证码错误");
        }
    }

    /**
     * 签发登录态（登录/注册共用）
     */
    private LoginResultVO issueLogin(MallUser user) {
        LoginUser loginUser = new LoginUser();
        // ⚠ 本行与下一处的 generateToken 必须成对一致（都是 USER_TYPE_USER）：
        //   网关按 JWT type claim 拼 Redis 键 panoramic:login:user:{id} 校验登录态，
        //   两处不一致 → 网关查不到会话 → 该端全部 401。
        loginUser.setUserType(LoginUser.USER_TYPE_USER);
        loginUser.setId(user.getId());
        // 账号即手机号：手机号同时作为快照的 username（LoginUser 无独立 phone 字段，手机号由本字段承载）
        loginUser.setUsername(user.getPhone());
        // ⚠ 不再写 loginUser.setNickname(...)：mall 端昵称的唯一来源是 customer-center 的顾客资料
        //    （读在 toCurrentUser 里取，见「昵称兜底」口径）。快照仍带 nickname 字段是 common 的公共形状，
        //    admin / store-bff 照旧在填，本端留空即可。
        loginUser.setStatus(user.getStatus());
        loginUser.setRoleIds(Collections.emptyList());
        loginUser.setPerms(new HashSet<>());
        loginUserCacheService.save(loginUser);

        String token = jwtService.generateToken(user.getId(), LoginUser.USER_TYPE_USER);
        return new LoginResultVO(token, toCurrentUser(loginUser));
    }

    /**
     * 登录快照 → 当前顾客信息（登录返回与 /auth/me 共用一处组装）
     * <p>⚠ 昵称为空的兜底<b>只在这一处</b>（spec §6.2）：域与资料页都不再各写一份。</p>
     */
    private CurrentUserVO toCurrentUser(LoginUser loginUser) {
        if (loginUser == null) {
            return null;
        }
        CurrentUserVO vo = new CurrentUserVO();
        vo.setId(loginUser.getId());
        vo.setUsername(loginUser.getUsername());
        // 账号即手机号：phone 直接取快照的 username，**不查库**（LoginUser 无独立 phone 字段）。
        // ⚠ 这是与 store-bff 的行为差异点：store-bff 从不填 phone，别"照着"把它删掉。
        vo.setPhone(loginUser.getUsername());
        vo.setPerms(loginUser.getPerms() == null
                ? List.of() : List.copyOf(loginUser.getPerms()));

        // 资料取自 customer-center，是**读增强**：取不到由 loadProfile 降级为 null（它内部已 catch + 告警），
        // **绝不阻断** /auth/me 与登录（同 CatalogBffService 对分类树的处理）。
        CustomerProfileVO profile = customerProfileBffService.loadProfile(loginUser.getId());
        String nickname = profile == null ? null : profile.getNickname();
        // ⚠ 昵称为空的兜底**只在这一处**（spec §6.2）：域与资料页都不再各写一份
        vo.setNickname(StringUtils.hasText(nickname) ? nickname : loginUser.getUsername());
        if (profile != null) {
            vo.setAvatar(profile.getAvatar());
            vo.setGender(profile.getGender());
            vo.setBirthday(profile.getBirthday());
        }
        return vo;
    }
}
