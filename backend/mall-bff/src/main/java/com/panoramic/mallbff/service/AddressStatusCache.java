package com.panoramic.mallbff.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.mallbff.vo.AddressStatusVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 「我的地址状态」缓存（Redis）——本端**唯一**的缓存设施，也是 mall-bff 里第一次直接用 Redis。
 *
 * <p>缓存的只有 {@link AddressStatusVO} 那种**控制流用的派生态**（有没有地址 + 默认地址 id），
 * <b>不放地址列表本身</b>：列表是给人看的数据，缓存它会引入陈旧展示；派生态陈旧有兜底
 * （陈旧 id 会被域侧 404 挡下，页面回退到重选）。口径见 {@code docs/contracts/mall-bff.md}
 * 的「地址状态读 / 地址状态缓存 / 缓存的三条硬口径」。</p>
 *
 * <p>⚠ <b>Redis 只是缓存，不是事实源</b>：本类三个方法**都不抛异常**，
 * 「读不到 / 写不进 / 删不掉」一律 {@code log.warn} 后按「没有缓存」继续——
 * 于是 Redis 整个不可用时本端退化成「每次多打一次 customer-center」，页面照常工作。
 * 反过来（让 Redis 故障冒泡）会把一个纯优化变成一个新增的故障面。</p>
 *
 * <p>⚠ <b>读失败与「没有地址」是两回事</b>，本类只负责存/取，不负责判定：调用方必须在
 * **下游读成功之后**才 {@link #put}——把一次 customer-center 故障写进缓存，等于把故障固化成
 * 整个 TTL 内的错结论（同 memory {@code bff-feign-cb-counts-4xx} 的教训）。</p>
 *
 * <p>⚠ 本端**不加载**额外的 Nacos 配置来做这件事：Redis 连接本来就由 {@code datasource-redis.yml}
 * 提供（经 {@code common-auth} 传递引入 {@code spring-boot-starter-data-redis}），故无 pom 改动、
 * 也不动 cross-cutting 第 12 条的加载矩阵。缓存键前缀与 TTL 走本服务自己的配置项（有默认值）。</p>
 */
@Slf4j
@Component
public class AddressStatusCache {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public AddressStatusCache(StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              @Value("${panoramic.mall.address-status-redis-prefix:panoramic:mall:addr-status}") String keyPrefix,
                              @Value("${panoramic.mall.address-status-ttl-seconds:1800}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * 取地址状态
     *
     * <p>⚠ 「没有」与「取不到」都返回 {@link Optional#empty()}，且都**按 miss 处理**（落回下游读）：
     * 对调用方而言两者的下一步动作相同，区分它们只会多一个分支、没有多一份信息。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，缓存键的维度；取自登录态）
     * @return 命中的状态；没有 / 读失败 / 解析失败都是空
     */
    public Optional<AddressStatusVO> find(Long customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(customerId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(json, AddressStatusVO.class));
        } catch (Exception e) {
            // ⚠ 含「解析失败」：说明缓存里是旧版本写的形状。不在这里删键——调用方随后必然在下游
            //    读成功时 put 一次把它覆盖掉，故这种不匹配是自愈的，删键只会多一次往返。
            log.warn("读取地址状态缓存失败（按未命中处理）: customerId={}", customerId, e);
            return Optional.empty();
        }
    }

    /**
     * 回填地址状态（⚠ 只允许在**下游读成功之后**调用，见类注释）
     *
     * <p>{@code hasAddress=false} 也照常回填：空结果是**合法状态不是 miss**，
     * 不缓存它的话，新顾客每次下单前都要穿透打一次 customer-center。</p>
     *
     * @param customerId 顾客账号 id（取自登录态）
     * @param status     派生出来的状态
     */
    public void put(Long customerId, AddressStatusVO status) {
        if (customerId == null || status == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key(customerId), objectMapper.writeValueAsString(status), ttl);
        } catch (Exception e) {
            log.warn("回填地址状态缓存失败（不影响本次结果）: customerId={}", customerId, e);
        }
    }

    /**
     * 失效地址状态（地址簿的**四个写路径成功后**调用：新增 / 编辑 / 删除 / 设默认）
     *
     * <p>⚠ 失败只告警：地址写已经成功，删键只是让派生态早一点刷新，兜底是 TTL。</p>
     *
     * @param customerId 顾客账号 id（取自登录态）
     */
    public void evict(Long customerId) {
        if (customerId == null) {
            return;
        }
        try {
            redisTemplate.delete(key(customerId));
        } catch (Exception e) {
            log.warn("失效地址状态缓存失败（地址写已成功，由 TTL 兜底）: customerId={}", customerId, e);
        }
    }

    /**
     * 缓存键：{@code {prefix}:{customerId}}（前缀默认 {@code panoramic:mall:addr-status}）
     *
     * <p>照 {@code LoginUserCacheService} 的键风格（前缀 + 冒号 + id），且先用前缀把本端的键隔开——
     * 本服务与登录态共用同一个 Redis 实例。</p>
     */
    private String key(Long customerId) {
        return keyPrefix + ":" + customerId;
    }
}
