package com.panoramic.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import com.panoramic.contract.trade.vo.TradeCartItemVO;
import com.panoramic.trade.entity.TradeCartItem;

import java.util.List;

/**
 * 购物车服务（trade-center 域下沉纯域）。
 * <p>own-entity CRUD 直接用 MyBatis-Plus 基类（IService）内置方法；本接口只承载购物车的八个领域入口
 * （与 {@code docs/contracts/trade-center.md} 的 8 行一一对应，方法名可与 Feign 声明不同、语义必须一致）。
 * 数据作用域 {@code customerId = mall_user.id}：八个方法**全按传入作用域过滤**，它即数据权限本身；
 * 不属于该顾客的行一律按「不存在」处理（404，不泄露存在性）。
 * ⚠ 作用域**不进路径段**（cross-cutting 第 22 条）：单参的三条（{@code listItems} / {@code countItems} /
 * {@code clearCart}）收裸 {@code customerId}，其余五条从各自 DTO 的字段里取（第 23 条）。
 * ⚠ 五个写方法的作用域**缺失即 400**（域应用层断言，见 {@code com.panoramic.trade.support.ScopeGuard}）：
 * DTO 上的 {@code @NotNull} 只覆盖 MVC 边界，绕过 MVC 的调用只有那道断言能挡——{@code null} 到了 SQL 层
 * 就变成「不限定」，会**静默**改到别人的行。
 * <b>本域不做任何鉴权/身份判断</b>——调用方传的 customerId 是否「本人」由 mall-bff 从登录态取，
 * 域侧不校验（防线在 BFF）。</p>
 * <p>⚠ 本表走<b>物理删除</b>（唯一键 {@code (customer_id, sku_id)} 与逻辑删除互斥）：
 * 删除相关的实现<b>不得</b>改调 {@code IService#remove*}，见 {@code TradeCartItemMapper} 类注释。</p>
 */
public interface TradeCartItemService extends IService<TradeCartItem> {

    /**
     * 列出该顾客的全部购物车行，按行 id 升序（= 加购顺序）。
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据作用域）
     * @return 购物车行列表；空购物车返回空列表（不返回 null、不抛 404）
     */
    List<TradeCartItemVO> listItems(Long customerId);

    /**
     * 购物车行数（角标用；行数 ≠ 件数，quantity 不影响计数）。
     *
     * @param customerId 顾客账号 id（数据作用域）
     * @return 行数；空购物车为 0
     */
    Integer countItems(Long customerId);

    /**
     * 加入购物车：同一 SKU 已有行则累加数量，否则新增一行（加入即选中）。
     *
     * @param dto        加购参数（**作用域** customerId + SPU / SKU / 数量 1..999）
     * @return 该 SKU 对应的购物车行 id（新增与累加都返回同一个语义）
     * @throws com.panoramic.common.exception.ServiceException 购物车已达 100 行上限（HTTP 400）
     */
    Long addItem(TradeCartItemAddDTO dto);

    /**
     * 修改单行数量（整份覆盖，不是增量）。
     *
     * @param id         购物车行 id
     * @param dto        新数量（1..999）+ **作用域** customerId
     * @throws com.panoramic.common.exception.ServiceException 行不存在或不属于该顾客（HTTP 404）
     */
    void updateQuantity(Long id, TradeCartItemUpdateDTO dto);

    /**
     * 设置单行选中状态。
     *
     * @param id         购物车行 id
     * @param dto        选中状态 + **作用域** customerId
     * @throws com.panoramic.common.exception.ServiceException 行不存在或不属于该顾客（HTTP 404）
     */
    void setItemSelected(Long id, TradeCartSelectDTO dto);

    /**
     * 全选 / 全不选（对该顾客名下全部行置同一选中态；<b>幂等</b>，0 行不报错）。
     * <p>⚠ 这是<b>域侧整表</b>操作，作用面含 mall-bff 眼里「已失效」的行——域不知道也不判可见性。</p>
     *
     * @param dto        选中状态 + **作用域** customerId
     */
    void setAllSelected(TradeCartSelectDTO dto);

    /**
     * 批量删除购物车行（<b>物理删除</b>；<b>幂等</b>：删 0 行不报错）。
     *
     * @param dto        待删除的行 id 列表（空列表直接返回）+ **作用域** customerId
     */
    void removeItems(TradeCartItemIdsDTO dto);

    /**
     * 清空购物车（<b>物理删除</b>该顾客名下全部行；幂等）。
     *
     * @param customerId 顾客账号 id（数据作用域）
     */
    void clearCart(Long customerId);
}
