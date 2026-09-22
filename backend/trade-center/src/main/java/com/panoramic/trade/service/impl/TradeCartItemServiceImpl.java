package com.panoramic.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import com.panoramic.contract.trade.vo.TradeCartItemVO;
import com.panoramic.trade.config.CartRedisCache;
import com.panoramic.trade.entity.TradeCartItem;
import com.panoramic.trade.mapper.TradeCartItemMapper;
import com.panoramic.trade.service.TradeCartItemService;
import com.panoramic.trade.support.ScopeGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 购物车服务实现（trade-center 域下沉纯域）。
 * <p>所有读写一律以「id + customerId」双条件限定作用域（域内不做鉴权，作用域是唯一防线；
 * 它由调用方携入 — 见 {@code Cross-cutting} 第 22 条）；
 * 不属于该顾客的行一律抛 404「购物车行不存在」，不区分「不存在」与「不归属」以免泄露存在性。</p>
 * <p>审计字段（create_user/update_user/create_time/update_time）由 common 的 {@code MyMetaObjectHandler}
 * 经 {@code UserContext} 自动填充，本类**不显式赋值**（Global Constraint 3）。</p>
 * <p>⚠ {@link CartRedisCache} 在本类里只是**旁路缓存/提示**：MySQL 是唯一事实源，Redis 失败或错判
 * 都不改变结果（两个方向的错判各自都有回退支路，见 {@link #addItem}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCartItemServiceImpl extends ServiceImpl<TradeCartItemMapper, TradeCartItem>
        implements TradeCartItemService {

    /**
     * 单购物车行数上限（spec：单车 ≤ 100 行，超出 400）。
     * <p>⚠ <b>软上限</b>：并发加购时两笔可双双通过检查、各插一行而略微越界（同
     * {@code CustomerAddressServiceImpl} 的 20 条地址上限口径）。刻意不为它加锁——上限是防滥用的护栏，
     * 不是不变量；为它引入跨行锁只会给每一次加购都加上代价。</p>
     */
    private static final int MAX_CART_ITEM_COUNT = 100;

    /**
     * 新行默认选中：加入即选中（用户视角「我刚加的东西应该在结算里」）
     */
    private static final int SELECTED_ON = 1;

    private final CartRedisCache cartRedisCache;

    @Override
    public List<TradeCartItemVO> listItems(Long customerId) {
        // 按 id 升序 = 加购顺序（不加别的排序键：购物车没有「置顶 / 最近操作」语义）
        return lambdaQuery()
                .eq(TradeCartItem::getCustomerId, customerId)
                .orderByAsc(TradeCartItem::getId)
                .list()
                .stream()
                .map(this::toVo)
                .toList();
    }

    @Override
    public Integer countItems(Long customerId) {
        // 读穿透缓存：命中直接回；未命中查库（count(*) 即**行数**，与 quantity 无关）并回写。
        // ⚠ 这里读的是「行数」不是「件数之和」——角标口径与列表行数必须一致，否则删一行而角标不动会像 bug。
        Integer cached = cartRedisCache.getCount(customerId);
        if (cached != null) {
            return cached;
        }
        // ⚠ 链式包装器的 count() 返回 Long（不是 long）：取值要走 intValue()，不能写成 (int) 强转
        int count = lambdaQuery().eq(TradeCartItem::getCustomerId, customerId).count().intValue();
        cartRedisCache.putCount(customerId, count);
        return count;
    }

    /**
     * 加购：同一 SKU 已有行则**累加数量**，否则新增一行。
     * <p>⚠ 两条支路<b>互为回退、缺一不可</b>（Redis 的提示与库之间没有强一致，两个方向都会错判）：</p>
     * <ol>
     *   <li><b>Redis 命中 → 原子自增</b>；若受影响 <b>0 行</b>（Redis <b>误报</b>：行其实不存在或已被删）
     *       → 落到插入支路；</li>
     *   <li>Redis 未命中（或误报回落）<b>→ 插入</b>；若插入撞唯一键 {@code (customer_id, sku_id)}
     *       （Redis <b>漏报</b>：行其实已存在）→ 回落到原子自增。</li>
     * </ol>
     * <p>两条路各自能兜住对方的错，最终都得到正确结果（只是多走一次库）。</p>
     * <p>⚠ 数量上限分两条路径封顶，不要只改一边：插入路径由 DTO 的 {@code @Max(999)} 拦，
     * 自增路径由 SQL 的 {@code LEAST(quantity + delta, 999)} 拦。</p>
     * <p>⚠ 累加**不得**退化成「先查出来 → +delta → 写回去」的读-改-写：并发下会丢更新
     * （详见 {@code TradeCartItemMapper#incrementQuantity}）。本方法的插入支路之所以安全，
     * 是因为唯一键把并发插入收敛成「一个成功、一个撞键」，而撞键的那一笔改走自增而不是报错。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addItem(TradeCartItemAddDTO dto) {
        // 作用域取自入参 DTO（cross-cutting 第 22/23 条）：不进路径段，域侧只做「按它筛」
        Long customerId = dto.getCustomerId();
        ScopeGuard.require(customerId, "顾客 id");
        Long skuId = dto.getSkuId();

        // ── 支路 1：提示集命中 → 直接原子自增（省去一次「先查后插」）
        if (cartRedisCache.isKnownSku(customerId, skuId)) {
            int affected = baseMapper.incrementQuantity(customerId, skuId, dto.getQuantity());
            if (affected > 0) {
                Long id = requireIdBySku(customerId, skuId);
                afterWrite(customerId, skuId);
                return id;
            }
            // 0 行 = 提示集误报（行已被并发删除，或 Set 里的成员其实是上一次清空前的残留）
            log.info("购物车 sku 提示集误报，回落为插入: customerId={}, skuId={}", customerId, skuId);
        }

        // ── 支路 2：插入新行
        // ⚠ 行数上限**只在确实要走插入时判**：重复加购一个既有 SKU 是「累加」，不该被「车满了」挡住
        //    （挡住的话用户连同一件商品都不能再加，且他清理购物车还得先删掉这行才能加回来）。
        // ⚠ 这里刻意查库而不是读 countItems 的缓存：上限是**真护栏**，判据必须来自唯一事实源；
        //    计数缓存只服务角标（可能短暂偏），拿它判上限会把「多查一次库」的优化变成「上限判不准」。
        long rowCount = lambdaQuery().eq(TradeCartItem::getCustomerId, customerId).count();
        if (rowCount >= MAX_CART_ITEM_COUNT) {
            throw new ServiceException(400, "购物车最多 100 种商品，请先清理");
        }
        TradeCartItem entity = new TradeCartItem();
        entity.setCustomerId(customerId);
        entity.setSpuId(dto.getSpuId());
        entity.setSkuId(skuId);
        entity.setQuantity(dto.getQuantity());
        entity.setSelected(SELECTED_ON);          // 加入即选中
        try {
            save(entity);                         // 走 MP 基类 → 触发审计自动填充
        } catch (DuplicateKeyException e) {
            // ⚠ 提示集**漏报**：行其实已存在，插入撞唯一键 uk_customer_sku。就地回落到自增支路
            //    （不是异常路径，是预期分支——Set 过期 / Redis 曾不可用 / 并发首插都会走到这里）。
            // ⚠ 接住它是必须的：不接的话域兜底回 HTTP 500 → 调用方按 5xx 计入熔断失败率
            //    （连点几次加购就能把熔断打开，此后正常请求也被降级）。**为什么这样安全**（两条缺一不可）：
            //    ① 本方法**没有内层 @Transactional 边界**，异常在方法体里被捕获、不穿过事务代理，
            //       Spring 不会把外层事务标成 rollback-only（标了的话后续语句会以
            //       UnexpectedRollbackException 收场，那才是真事故）；
            //    ② MySQL / InnoDB 的重复键错误**只让当前语句失败、不中止整个事务**（与 PostgreSQL 不同），
            //       随后的 UPDATE 可正常执行并提交。先到者已提交的那一行，正是我们要累加的目标行。
            log.info("购物车 sku 提示集漏报，回落为原子自增: customerId={}, skuId={}", customerId, skuId);
            baseMapper.incrementQuantity(customerId, skuId, dto.getQuantity());
            Long id = requireIdBySku(customerId, skuId);
            afterWrite(customerId, skuId);
            return id;
        }
        afterWrite(customerId, skuId);
        return entity.getId();                    // IdType.AUTO：id 由 DB 生成后回填
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long id, TradeCartItemUpdateDTO dto) {
        // 作用域取自入参 DTO（cross-cutting 第 22/23 条）：不进路径段，域侧只做「按它筛」
        Long customerId = dto.getCustomerId();
        ScopeGuard.require(customerId, "顾客 id");
        // ⚠ 用 lambdaUpdate().set(...) 而非 updateById(entity)：语义是「整份覆盖数量」，
        //    updateById 会跳过 null 列、且要求先读出整行。
        // ⚠ 已知取舍：ChainUpdate#update() 走的是 update(null)，**不触发审计自动填充**，
        //    故本支路不刷新 update_user（update_time 由 DDL 的 ON UPDATE CURRENT_TIMESTAMP 推进）。
        //    当前唯一写者是顾客本人，值与 insert 时相同，无可观测影响（与 customer-center 的口径一致）。
        boolean updated = lambdaUpdate()
                .eq(TradeCartItem::getId, id)
                .eq(TradeCartItem::getCustomerId, customerId)   // 锚点：域内不做鉴权，作用域只能靠它
                .set(TradeCartItem::getQuantity, dto.getQuantity())
                .update();
        // ⚠ 受影响行数**必须判**（不是多余的防御）：两个标签页并发时，A 在本语句之前删掉该行 →
        //    本语句匹配 0 行、**不抛异常**，调用方却拿到成功回执、页面显示「已修改」——**成功是假的**。
        //    为 0 行一律按「行不存在」回 404，口径与其它按 id 改写的方法一致。
        if (!updated) {
            throw new ServiceException(404, "购物车行不存在");
        }
        // 数量变化不影响行数，本次 DEL 严格说不是必需的；保留它是为了让「写路径结束一律 DEL 计数」
        // 成为一条无例外的规则——少一个要判的分支，就少一个将来改错的地方。
        cartRedisCache.evictCount(customerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setItemSelected(Long id, TradeCartSelectDTO dto) {
        Long customerId = dto.getCustomerId();   // 作用域取自入参 DTO（cross-cutting 第 22/23 条）
        ScopeGuard.require(customerId, "顾客 id");
        boolean updated = lambdaUpdate()
                .eq(TradeCartItem::getId, id)
                .eq(TradeCartItem::getCustomerId, customerId)   // ⚠ 必须带 customerId：只按 id 更新等于开了越权写入口
                .set(TradeCartItem::getSelected, toSelectedValue(dto.getSelected()))
                .update();
        if (!updated) {
            throw new ServiceException(404, "购物车行不存在");
        }
        // 不动计数缓存：选中位与行数无关，本条不改变任何影响计数的事实（与 updateQuantity 的 DEL 不冲突）
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAllSelected(TradeCartSelectDTO dto) {
        Long customerId = dto.getCustomerId();   // 作用域取自入参 DTO（cross-cutting 第 22/23 条）
        ScopeGuard.require(customerId, "顾客 id");
        // ⚠ 这是**域侧整表**操作（按 customerId 一把改），作用面含 mall-bff 眼里「已失效」的行——
        //    域不持商品、不知道也不判可见性（见 docs/contracts/trade-center.md 第一节最后一条）。
        //    失效行被一并改写无副作用：调用方不展示它，结算也未接入。
        // ⚠ **幂等**：全选时若本来全选中，受影响 0 行——这是正常的（不是「行不存在」），故不判受影响行数。
        lambdaUpdate()
                .eq(TradeCartItem::getCustomerId, customerId)
                .set(TradeCartItem::getSelected, toSelectedValue(dto.getSelected()))
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItems(TradeCartItemIdsDTO dto) {
        Long customerId = dto.getCustomerId();   // 作用域取自入参 DTO（cross-cutting 第 22/23 条）
        ScopeGuard.require(customerId, "顾客 id");
        List<Long> ids = dto.getIds();
        if (ids == null || ids.isEmpty()) {
            // 空列表直接返回：别拼出 `IN ()`（语法错误 → 域兜底 500，而这次「什么都没删」本该是成功的空操作）
            return;
        }
        baseMapper.physicalDeleteByIds(customerId, ids);
        // ⚠ **幂等**：删 0 行不报错。批量语义下不该因为「某一行被另一个标签页并发删掉了」而让整批失败
        //    ——调用方（多选删除按钮）要的结果是「这些行现在都不在车里了」，而 0 行影响的含义正是如此。
        //    （对照：按 id 改单行那条路 0 行必须报 404，因为「改成功」与「没这行」对调用方是两件事。）
        //
        // ⚠ 这里**不逐 id 去 forgetSku**：ids 是**行 id 不是 skuId**，要清提示集就得先查一次 skuId。
        //    为省这一次查询，改由 Set 的 TTL 兜底——残留的 sku 只影响「下一次加购走哪条支路」，
        //    而两条支路都能得到正确结果（见 addItem）。清空购物车那条路则不同：一次就能清掉整个 Set
        //    （forgetAllSkus），故那边照清。
        cartRedisCache.evictCount(customerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long customerId) {
        // ⚠ 失败形态虽然温和（customer_id = NULL 删 0 行，不会误删别人的行），但**写路径一律过护栏**：
        //    留一条不过护栏的写，等于把「写侧必经 ScopeGuard」写成一句不成立的话，
        //    下一个人照它推断「没护栏的一定是读」，就会漏掉真正的洞。
        ScopeGuard.require(customerId, "顾客 id");
        baseMapper.physicalDeleteByCustomer(customerId);
        // 幂等：本来就是空车时删 0 行，同样清一遍缓存（无副作用）
        cartRedisCache.forgetAllSkus(customerId);
        cartRedisCache.evictCount(customerId);
    }

    /**
     * 按唯一键 {@code (customerId, skuId)} 点查行 id（走唯一键索引，代价可忽略）
     * <p>⚠ 存在的意义是**换取稳定的契约出参**：自增路径拿不到 id（{@code UPDATE} 不回传主键），
     * 而加购接口的契约出参就是行 id（调用方据此做「定位到那一行」），故这里补一次点查。</p>
     * <p>⚠ 点查为空的情况是**并发物理删除赢在本次自增之后**（同一顾客另一个标签页点了清空 / 删除）。
     * 此时不能返回 null——契约出参是 {@code Long}，回 null 等于给调用方一个「加购成功但没有 id」的
     * 假成功（HTTP 200 + 空 body）；按与兄弟方法同口径的 404 回更诚实：那一行确实已经不在车里了。</p>
     */
    private Long requireIdBySku(Long customerId, Long skuId) {
        TradeCartItem row = lambdaQuery()
                .select(TradeCartItem::getId)
                .eq(TradeCartItem::getCustomerId, customerId)
                .eq(TradeCartItem::getSkuId, skuId)
                .one();
        if (row == null) {
            throw new ServiceException(404, "购物车行不存在");
        }
        return row.getId();
    }

    /**
     * 写路径的缓存收尾（两条支路都要走）：记住这个 sku + 失效行数计数
     * <p>⚠ 顺序放在**拿到稳定结果之后**（{@code requireIdBySku} 可能抛 404）：抛异常时事务回滚，
     * 若提示集已写进去就成了误报——虽然误报也有回退支路兜住（自增 0 行 → 回插），但没必要造它。</p>
     */
    private void afterWrite(Long customerId, Long skuId) {
        cartRedisCache.rememberSku(customerId, skuId);
        cartRedisCache.evictCount(customerId);
    }

    /**
     * 实体的 {@code Integer selected}（0/1）→ 视图的 {@code Boolean selected}
     */
    private Boolean toSelectedValue(Integer selected) {
        return selected != null && selected == SELECTED_ON;
    }

    /**
     * 请求的 {@code Boolean selected} → 实体的 {@code Integer selected}（0/1）
     */
    private Integer toSelectedValue(Boolean selected) {
        return Boolean.TRUE.equals(selected) ? SELECTED_ON : 0;
    }

    /**
     * 实体 → 视图对象
     */
    private TradeCartItemVO toVo(TradeCartItem entity) {
        TradeCartItemVO vo = new TradeCartItemVO();
        vo.setId(entity.getId());
        vo.setSpuId(entity.getSpuId());
        vo.setSkuId(entity.getSkuId());
        vo.setQuantity(entity.getQuantity());
        vo.setSelected(toSelectedValue(entity.getSelected()));
        return vo;
    }
}
