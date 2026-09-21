package com.panoramic.trade.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 购物车 Redis 旁路缓存（两个键、两个角色，都是**性能优化**，不是事实源）。
 * <p><b>⚠ 本类只做性能优化，与库内数据无强一致要求：MySQL（{@code trade_cart_item}）是唯一事实源。</b>
 * Redis 里没有任何登录态、也不参与任何身份判断——本域仍然不做鉴权（见
 * {@code TradeUserIdentityFilter} / {@code TradeSecurityConfig}），
 * 「本域为什么是全仓唯一加载 Redis 的业务域」见 {@code application.yml} 末尾注释与
 * {@code docs/contracts/trade-center.md} 第一节。</p>
 * <p><b>⚠ Redis 不可用时的取向：所有方法吞异常 + {@code log.warn} 降级，绝不让加购因为 Redis 失败。</b>
 * 读方法失败即返回「未命中」语义，写方法失败即当没写过——两条路都退化成纯查库，正确性不受影响
 * （只是少了去重提示 / 计数要多查一次库）。故本类<b>不抛异常</b>，也不做任何重试。</p>
 * <p>两个键：</p>
 * <ul>
 *   <li>{@code panoramic:cart:skus:{customerId}} —— 类型 <b>Set</b>，成员是 skuId 的字符串形式，
 *       TTL 24 小时。用途：加购第一遍重复过滤（命中即走原子自增，免去一次「先查后插」）。
 *       ⚠ <b>只放 id，绝不放数量</b>——数量放进来就有两个事实源、就有数值分歧，
 *       而这里的目的仅仅是「这个 spu/sku 在这辆车里大概率已有行」。</li>
 *   <li>{@code panoramic:cart:count:{customerId}} —— 类型 <b>String</b>，值=行数，TTL 60 秒。
 *       用途：购物车行数（角标）读穿透缓存。
 *       ⚠ <b>不许用 {@code INCRBY} 之类的算式维护</b>（加购 +1、删除 -1 看着很自然，但每次漏算/多算都会
 *       永久偏掉，且无处修正）：口径一律「<b>写路径 DEL、读路径穿透</b>」——任何写路径结束就删键，
 *       读路径未命中就算库并回写。最坏情况只是少一次命中（多查一次库），**不会算错**。</li>
 * </ul>
 * <p>两处失效都可自愈（两个方向的错判都不影响结果，只是多走一次库）：</p>
 * <ul>
 *   <li><b>误报</b>（Set 里有、库里其实没这行）：自增匹配 0 行 → 回落到插入；</li>
 *   <li><b>漏报</b>（Set 里没有、库里其实有这行）：插入撞唯一键 {@code (customer_id, sku_id)} →
 *       回落到自增。</li>
 * </ul>
 * <p>⚠ 计数器与库之间还有一层**已知的短暂不一致**（设计上接受，故计数只用于角标）：
 * 写路径先落库、再 DEL 键，中间若有并发读，它会用**提交前**的值回写该键，于是这个值可能
 * 在 TTL（60 秒）内偏掉，随后自愈。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartRedisCache {

    /**
     * 「这辆车里有哪些 sku」的键前缀（Set，成员=skuId 字符串）
     */
    private static final String SKU_KEY_PREFIX = "panoramic:cart:skus:";

    /**
     * 「这辆车几行」的键前缀（String，值=行数）
     */
    private static final String COUNT_KEY_PREFIX = "panoramic:cart:count:";

    /**
     * sku 提示集 TTL：24 小时（每次加购续期）。过期后最坏情况是漏报一次 → 靠唯一键冲突回落自增兜住，
     * 故这个值只影响命中率、不影响正确性。
     */
    private static final Duration SKU_TTL = Duration.ofHours(24);

    /**
     * 计数缓存 TTL：60 秒。刻意很短——它只是给角标挡住「每次刷新都 count(*)」，
     * 而不是要长期承担正确性；写路径每次都会 DEL，TTL 只是兜住「DEL 失败/键被外部改坏」这类残留。
     */
    private static final Duration COUNT_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 这个 sku 是否已在该顾客的车里（加购的第一遍重复过滤）。
     * <p>⚠ 返回 {@code false} 的语义是「<b>不知道</b>」，不是「确定不在」——Redis 不可用 / 读失败一律退化为此值，
     * 调用方据此走插入支路（撞唯一键再回落），故<b>误判不会写坏数据</b>。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param skuId      店铺商品 SKU id
     * @return true=已知有；false=未知或确定没有
     */
    public boolean isKnownSku(Long customerId, Long skuId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForSet()
                    .isMember(skuKey(customerId), String.valueOf(skuId)));
        } catch (Exception e) {
            log.warn("购物车 sku 提示集读取失败，退化为直接查库: customerId={}, skuId={}", customerId, skuId, e);
            return false;
        }
    }

    /**
     * 记住这个 sku 已在该顾客的车里（加购成功后调用，顺带续期 TTL）
     *
     * @param customerId 顾客账号 id
     * @param skuId      店铺商品 SKU id
     */
    public void rememberSku(Long customerId, Long skuId) {
        try {
            String key = skuKey(customerId);
            stringRedisTemplate.opsForSet().add(key, String.valueOf(skuId));
            // 续期：Set 一直活着也无妨（它只是提示），但长期不清理会攒下大量死键
            stringRedisTemplate.expire(key, SKU_TTL);
        } catch (Exception e) {
            log.warn("购物车 sku 提示集写入失败，忽略: customerId={}, skuId={}", customerId, skuId, e);
        }
    }

    /**
     * 忘掉单个 sku。
     * <p>⚠ 当前<b>没有任何调用方</b>：批量删除拿到的是行 id、不是 skuId，为省一次查询改由 Set 的 TTL 兜底
     * （残留的 sku 只影响「下一次加购走哪条支路」，两条支路都能得到正确结果，见类注释）。
     * 保留它是给「已经知道 skuId」的删除场景（如将来的单行删除 / 下架清理）用的，
     * 不要因为暂时没人调就删掉，也不要为了用它而在批量删除里多查一次库。</p>
     *
     * @param customerId 顾客账号 id
     * @param skuId      店铺商品 SKU id
     */
    public void forgetSku(Long customerId, Long skuId) {
        try {
            stringRedisTemplate.opsForSet().remove(skuKey(customerId), String.valueOf(skuId));
        } catch (Exception e) {
            log.warn("购物车 sku 提示集删除失败，忽略: customerId={}, skuId={}", customerId, skuId, e);
        }
    }

    /**
     * 忘掉整车（清空购物车时调用）——直接删整个 Set，不留残键
     *
     * @param customerId 顾客账号 id
     */
    public void forgetAllSkus(Long customerId) {
        try {
            stringRedisTemplate.delete(skuKey(customerId));
        } catch (Exception e) {
            log.warn("购物车 sku 提示集清空失败，忽略: customerId={}", customerId, e);
        }
    }

    /**
     * 读行数缓存。
     *
     * @param customerId 顾客账号 id
     * @return 缓存中的行数；<b>不存在（或 Redis 不可用）返回 {@code null}</b> —— 调用方据此穿透查库
     */
    public Integer getCount(Long customerId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(countKey(customerId));
            if (value == null || value.isBlank()) {
                return null;
            }
            return Integer.valueOf(value.trim());
        } catch (Exception e) {
            // 值被外部改坏（NumberFormatException）也走这里：按未命中处理，读路径穿透查库即自愈
            log.warn("购物车计数缓存读取失败，退化为穿透查库: customerId={}", customerId, e);
            return null;
        }
    }

    /**
     * 回写行数缓存（读路径穿透查库后调用）
     *
     * @param customerId 顾客账号 id
     * @param count      行数
     */
    public void putCount(Long customerId, int count) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(countKey(customerId), String.valueOf(count), COUNT_TTL);
        } catch (Exception e) {
            log.warn("购物车计数缓存写入失败，忽略: customerId={}, count={}", customerId, count, e);
        }
    }

    /**
     * 失效行数缓存（<b>所有写路径结束都要调</b>）——只 DEL、不做算式
     *
     * @param customerId 顾客账号 id
     */
    public void evictCount(Long customerId) {
        try {
            stringRedisTemplate.delete(countKey(customerId));
        } catch (Exception e) {
            log.warn("购物车计数缓存失效失败，忽略: customerId={}", customerId, e);
        }
    }

    private String skuKey(Long customerId) {
        return SKU_KEY_PREFIX + customerId;
    }

    private String countKey(Long customerId) {
        return COUNT_KEY_PREFIX + customerId;
    }
}
