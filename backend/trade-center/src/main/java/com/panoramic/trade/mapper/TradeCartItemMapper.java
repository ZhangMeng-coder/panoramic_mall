package com.panoramic.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.panoramic.trade.entity.TradeCartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;

/**
 * 购物车行 Mapper。
 * <p>own-entity 的增删改查一律走 MP 基类（{@code BaseMapper}）；本接口只补两类基类给不了的能力：
 * <b>物理删除</b>与<b>条件原子自增</b>。</p>
 *
 * <p><b>① 为什么购物车走物理删除</b>（而不是全仓其它实体的逻辑删除）：
 * 本表有唯一键 {@code uk_customer_sku (customer_id, sku_id)}，而逻辑删除只把 {@code is_delete} 置 1、
 * <b>行仍在表里、仍参与唯一键</b>——于是「删掉某 SKU 再加回来」这种最普通不过的操作会直接撞唯一键
 * （用户已裁定：购物车走物理删除）。故本表 {@code is_delete} <b>恒 0</b>，该列只为对齐
 * {@link com.panoramic.common.vo.BaseEntity}（{@code @TableLogic}）而保留，没有业务含义。
 * ⚠ 由此也得出一条纪律：<b>本表禁止调用 {@code removeById} / {@code remove(...)} / {@code IService#remove*}</b>
 * ——那是逻辑删除，会立刻把「再加购同一 SKU」变成 500。删行只走本接口的三个
 * {@code physicalDelete*} 方法。</p>
 *
 * <p><b>⚠ 本接口（含 {@code BaseMapper} 的内置方法）只允许被本域 service 调用</b>
 * （{@code TradeCartItemServiceImpl}），调用方（mall-bff）一律经内部 Feign 走域 service：
 * 这些方法都按 {@code customer_id} 收窄作用域，直连调用等于绕过数据权限锚点。</p>
 */
public interface TradeCartItemMapper extends BaseMapper<TradeCartItem> {

    /**
     * 物理删除一行（按「行 id + 顾客锚点」双条件）。
     * <p>⚠ 锚点 {@code customerId} <b>必须带</b>：域内不做鉴权，锚点是唯一防线（口径同
     * {@code CustomerAddressServiceImpl#deleteAddress}）。</p>
     * <p>⚠ 当前 service 里<b>没有调用方</b>：页面的「删除单行」与「删除选中」走的是下面的批量删除
     * （一个 id 的批量删除与它等价），保留它是给「确实只删一行、不想拼集合」的场景用的。
     * 别为了用它而把批量删除拆成逐行调用——那是 N 次往返。</p>
     *
     * @param id         购物车行 id
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 受影响行数（0 = 不存在或不属于该顾客）
     */
    @Delete("DELETE FROM trade_cart_item WHERE id = #{id} AND customer_id = #{customerId}")
    int physicalDeleteById(@Param("id") Long id, @Param("customerId") Long customerId);

    /**
     * 物理批量删除（按「行 id IN (...) + 顾客锚点」双条件）。
     * <p>⚠ {@code ids} 为空时<b>不要调用</b>：会拼出 {@code IN ()} 这种非法 SQL（service 已短路拦截）。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param ids        购物车行 id 集合（非空）
     * @return 受影响行数（可以小于 ids 个数：并发删除 / 或有不属于自己的 id）
     */
    @Delete("<script>"
            + "DELETE FROM trade_cart_item WHERE customer_id = #{customerId} AND id IN "
            + "<foreach collection='ids' item='itemId' open='(' separator=',' close=')'>#{itemId}</foreach>"
            + "</script>")
    int physicalDeleteByIds(@Param("customerId") Long customerId, @Param("ids") Collection<Long> ids);

    /**
     * 物理清空某顾客的全部购物车行
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 受影响行数（0 = 本来就是空车，幂等）
     */
    @Delete("DELETE FROM trade_cart_item WHERE customer_id = #{customerId}")
    int physicalDeleteByCustomer(@Param("customerId") Long customerId);

    /**
     * 条件原子自增（「查重后加数量」的核心）。
     * <p>⚠ <b>必须用这条 SQL，不能写成「先 {@code getById} 读出来 +1 再 updateById」</b>：
     * 读-改-写在并发下会丢失更新——两个标签页同时加购，都读到 3、都写回 4，结果丢了 1 件。
     * 这里把 {@code quantity + delta} 交给 DB 在同一语句内完成，行锁天然串行化。</p>
     * <p>⚠ 数量上限在 SQL 里用 {@code LEAST(..., 999)} 封顶，而不是靠先读后判：
     * 自增路径不经过 DTO 的 {@code @Max(999)}（那是插入路径的闸），不在这里封顶的话，
     * 反复加购同一 SKU 就能把 quantity 顶到 INT 上限。</p>
     * <p>⚠ {@code is_delete = 0} 手写进条件：本表 {@code is_delete} 恒 0（物理删除），
     * 且 {@code @TableLogic} 对手写 SQL <b>不生效</b>——不写就没有这一层过滤。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param skuId      店铺商品 SKU id
     * @param delta      增量（&gt;0；由 DTO 的 {@code @Min(1)} 保证）
     * @return 受影响行数（<b>0 = Redis 误报：这行其实不存在或已被删</b> → 调用方回落为插入）
     */
    @Update("UPDATE trade_cart_item SET quantity = LEAST(quantity + #{delta}, 999) "
            + "WHERE customer_id = #{customerId} AND sku_id = #{skuId} AND is_delete = 0")
    int incrementQuantity(@Param("customerId") Long customerId,
                          @Param("skuId") Long skuId,
                          @Param("delta") int delta);
}
