package com.panoramic.contract.trade.api;

import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import com.panoramic.contract.trade.dto.TradeOrderCreateDTO;
import com.panoramic.contract.trade.dto.TradeOrderPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderPayDTO;
import com.panoramic.contract.trade.dto.TradeOrderPlatformPageQueryDTO;
import com.panoramic.contract.trade.dto.TradeOrderShipDTO;
import com.panoramic.contract.trade.vo.TradeCartItemVO;
import com.panoramic.contract.trade.vo.TradeOrderPageVO;
import com.panoramic.contract.trade.vo.TradeOrderVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * trade-center（交易域，下沉纯域）内部 Feign 客户端。
 * <p>交易域持「购物车 {@code trade_cart_item}」与「订单」（{@code trade_order} 等 5 张表）；
 * 结算 / 评价不在本期（见仓库根 {@code todo.md}）。不向页面暴露公网路由，
 * 只被端 BFF 经本接口内部调用。规约（见 CLAUDE.md 与 docs/contracts/trade-center.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 trade-center-interface（域服务端、各端 BFF 客户端引用同一份类型）；</li>
 *   <li>方法直接返回业务结果类型（不包 RespData），错误走异常统一传播；</li>
 *   <li>调用经 {@link TradeFeignConfiguration} 附带信任头 + 透传主身份 + 错误解码；熔断由<b>调用方</b>经 Nacos
 *       {@code feign-circuitbreaker.yml} 配置提供，不在本类。</li>
 * </ul>
 * <b>数据权限口径分两种，别按一种理解</b>：
 * <ul>
 *   <li><b>购物车无分侧</b>：锚点 {@code customerId} = {@code mall_user.id}（跨域 id 引用、无外键），
 *       全部方法按传入锚点过滤，只做 C 端自助。</li>
 *   <li><b>订单分三侧</b>（顾客 / 商户 / 平台）：顾客侧锚点 {@code customerId}、商户侧锚点 {@code storeId}
 *       ——**锚点在路径里**；平台侧<b>没有锚点段</b>（管理端本就是全量视角），它要筛的
 *       {@code storeId} / {@code customerId} 走 {@link TradeOrderPlatformPageQueryDTO} 的字段。
 *       订单标识一律用 {@code orderNo}（业务可读单号），不用自增 id。</li>
 * </ul>
 * 两种口径下域内都<b>不做任何身份判断</b>（不读 X-User-Type 判权；该头只用于审计留痕）：
 * 调用方传的锚点是否真是「本人 / 本店」，由端 BFF 从登录态取，域侧不校验——防线在 BFF。
 * 服务端路径与映射需与 trade-center 域内部控制器一一对应（前缀 /internal/trade，类级再拼 /cart 或 /order）。
 * <p>⚠ <b>字段定义不在契约表里</b>：字段即 {@code com.panoramic.contract.trade.dto} / {@code .vo} 包下的类，
 * 契约表（docs/contracts/trade-center.md）只登记「有哪些接口、形状是什么、类型在哪、谁在调」，**不抄字段**
 * （抄一份就是制造第二个会漂移的地方）。</p>
 * <p>⚠ 方法<b>按行分批补齐</b>：摘掉契约表某行的 {@code 待实现} 标记、在此声明该方法、域侧补上实现，
 * 三者必须落在同一个提交里（否则 drift-check 的标记腐烂反向哨兵会报错）。
 * 当前购物车 8 条 + 订单 10 条**全部落地**（契约表 {@code 待实现} 归零）。</p>
 */
@FeignClient(name = "trade-center", contextId = "tradeCenterClient",
        path = "/internal/trade", configuration = TradeFeignConfiguration.class)
public interface TradeCenterClient {

    // ── 购物车（8 条，C 端顾客自助，无分侧） ──────────────────────────────────────

    /**
     * 我的购物车行列表，按行 id 升序（= 加购顺序）；空购物车返回空列表，不返回 null。
     * <p>⚠ 返回的是**原始行**：域不判「这一行还算不算可买」（店铺是否已审核 / SPU 是否上架 / 是否被平台锁定），
     * 可见性由 mall-bff 读时重判并打 {@code invalid} 标记——域内不持商品、也不做任何内容裁决。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 购物车行列表（空则为空列表）
     */
    @GetMapping("/cart/{customerId}")
    List<TradeCartItemVO> listCartItems(@PathVariable("customerId") Long customerId);

    /**
     * 购物车<b>行数</b>（不是件数之和：同一 SPU 的不同 SKU 各算一行，quantity 不影响计数）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @return 行数；空购物车为 0
     */
    @GetMapping("/cart/{customerId}/count")
    Integer cartItemCount(@PathVariable("customerId") Long customerId);

    /**
     * 加入购物车：同一 {@code (customerId, skuId)} 已存在则<b>累加数量</b>（不新增行），否则新增一行
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        加购参数（SPU / SKU / 数量）
     * @return 该 SKU 对应的**购物车行 id**（新增或既有行都返回同一个语义：这条 SKU 在车里的那一行）
     * @throws com.panoramic.common.exception.ServiceException 单车已达 100 行上限（HTTP 400）
     */
    @PostMapping("/cart/{customerId}/items")
    Long addCartItem(@PathVariable("customerId") Long customerId,
                     @RequestBody TradeCartItemAddDTO dto);

    /**
     * 修改单行数量（**整份覆盖**，不是增量）；行不存在或不属于该顾客 → 404，不区分两种情形
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param id         购物车行 id
     * @param dto        新数量（1..999）
     */
    @PutMapping("/cart/{customerId}/items/{id}")
    void updateCartItemQuantity(@PathVariable("customerId") Long customerId,
                                @PathVariable("id") Long id,
                                @RequestBody TradeCartItemUpdateDTO dto);

    /**
     * 设置单行选中状态；行不存在或不属于该顾客 → 404，不区分两种情形
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param id         购物车行 id
     * @param dto        选中状态
     */
    @PutMapping("/cart/{customerId}/items/{id}/selected")
    void setCartItemSelected(@PathVariable("customerId") Long customerId,
                             @PathVariable("id") Long id,
                             @RequestBody TradeCartSelectDTO dto);

    /**
     * 全选 / 全不选：对该顾客<b>名下全部行</b>置同一选中态（幂等，0 行不报错）
     * <p>⚠ 这是<b>域侧整表</b>操作，作用面包含 mall-bff 眼里「已失效」的行（域不知道也不判可见性）——
     * 失效行被一并改写无副作用：调用方不展示它、结算也未接入。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        选中状态
     */
    @PutMapping("/cart/{customerId}/selected")
    void setAllCartItemsSelected(@PathVariable("customerId") Long customerId,
                                 @RequestBody TradeCartSelectDTO dto);

    /**
     * 批量删除购物车行（**物理删除**）——<b>幂等</b>：删 0 行不报错（多选删除不该因某行被并发删掉而整体失败）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        待删除的**行 id** 列表（空列表直接返回，不做任何删除）
     */
    @PostMapping("/cart/{customerId}/items/remove")
    void removeCartItems(@PathVariable("customerId") Long customerId,
                         @RequestBody TradeCartItemIdsDTO dto);

    /**
     * 清空购物车（**物理删除**该顾客名下全部行）；幂等
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     */
    @DeleteMapping("/cart/{customerId}")
    void clearCart(@PathVariable("customerId") Long customerId);

    // ── 订单（10 条，分顾客 / 商户 / 平台三侧） ──────────────────────────────────

    /**
     * 下单：一次提交按 {@code storeId} 拆成多笔（一单一店），**返回整批**，顺序 = {@code storeId} 升序
     *
     * <p>⚠ <b>幂等</b>：命中请求级键（{@code requestId}）或窗口内同指纹时，<b>原样返回首次那批</b>——
     * 不重建、不二次扣库存、连商品都不再校验。故调用方拿到的一律是「这次提交对应的那一批」。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        下单参数（来源 / 幂等键 / 地址快照 / 商品行）
     * @return 本次提交的整批订单（至少一笔）
     * @throws com.panoramic.common.exception.ServiceException 商品不可购买 / 库存不足 / 数量越界 / 地址非法（HTTP 400）
     */
    @PostMapping("/order/{customerId}")
    List<TradeOrderVO> createOrder(@PathVariable("customerId") Long customerId,
                                   @RequestBody TradeOrderCreateDTO dto);

    /**
     * 我的订单分页（只含该顾客的订单；按主键倒序 = 下单倒序）
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param dto        筛选与分页条件（无锚点字段）
     * @return 总数 + 当页订单
     */
    @PostMapping("/order/customer/{customerId}/page")
    TradeOrderPageVO pageCustomerOrders(@PathVariable("customerId") Long customerId,
                                        @RequestBody TradeOrderPageQueryDTO dto);

    /**
     * 我的订单详情；订单不存在或不属于该顾客 → 404，不区分两种情形
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param orderNo    业务可读单号
     * @return 订单（状态 + 地址快照 + 明细齐全）
     */
    @GetMapping("/order/customer/{customerId}/{orderNo}")
    TradeOrderVO getCustomerOrder(@PathVariable("customerId") Long customerId,
                                  @PathVariable("orderNo") String orderNo);

    /**
     * 支付（假支付）：{@code dto.amount} 必须<b>等于订单总额</b>，不一致 → 400
     *
     * <p>⚠ <b>不幂等，重复提交由状态机拒</b>：第二次 {@code pay} 回 400「订单状态不能从「已支付」重复变更到
     * 「已支付」」。调用方**原样透传**该 4xx，不另译成「请勿重复操作」（同一句提示只此一份）。</p>
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param orderNo    业务可读单号
     * @param dto        支付金额
     * @throws com.panoramic.common.exception.ServiceException 金额不符 / 非法迁移（HTTP 400）、订单不属本人（404）
     */
    @PostMapping("/order/customer/{customerId}/{orderNo}/pay")
    void payOrder(@PathVariable("customerId") Long customerId,
                  @PathVariable("orderNo") String orderNo,
                  @RequestBody TradeOrderPayDTO dto);

    /**
     * 确认收货（终态）；已收货再调用 → 400
     *
     * @param customerId 顾客账号 id（= mall_user.id，数据权限锚点）
     * @param orderNo    业务可读单号
     * @throws com.panoramic.common.exception.ServiceException 非法迁移（HTTP 400）、订单不属本人（404）
     */
    @PostMapping("/order/customer/{customerId}/{orderNo}/receive")
    void receiveOrder(@PathVariable("customerId") Long customerId,
                      @PathVariable("orderNo") String orderNo);

    /**
     * 本店订单分页（只含该店铺的订单；**含待支付**——店主的待办起点不是「已支付」）
     *
     * @param storeId 店铺 id（= 店主账号 id，数据权限锚点）
     * @param dto     筛选与分页条件（无锚点字段）
     * @return 总数 + 当页订单
     */
    @PostMapping("/order/store/{storeId}/page")
    TradeOrderPageVO pageStoreOrders(@PathVariable("storeId") Long storeId,
                                     @RequestBody TradeOrderPageQueryDTO dto);

    /**
     * 本店订单详情；订单不存在或不属于该店铺 → 404，不区分两种情形
     *
     * @param storeId 店铺 id（= 店主账号 id，数据权限锚点）
     * @param orderNo 业务可读单号
     * @return 订单
     */
    @GetMapping("/order/store/{storeId}/{orderNo}")
    TradeOrderVO getStoreOrder(@PathVariable("storeId") Long storeId,
                               @PathVariable("orderNo") String orderNo);

    /**
     * 发货（记录快递单号）；重复发货 / 跳级 → 400
     *
     * @param storeId 店铺 id（= 店主账号 id，数据权限锚点）
     * @param orderNo 业务可读单号
     * @param dto     快递单号（必填）
     * @throws com.panoramic.common.exception.ServiceException 单号为空 / 非法迁移（HTTP 400）、订单不属本店（404）
     */
    @PostMapping("/order/store/{storeId}/{orderNo}/ship")
    void shipOrder(@PathVariable("storeId") Long storeId,
                   @PathVariable("orderNo") String orderNo,
                   @RequestBody TradeOrderShipDTO dto);

    /**
     * 平台侧订单分页（**全量视角、路径无锚点**）：要筛店铺 / 顾客走 DTO 字段
     *
     * @param dto 筛选与分页条件（含可选的 {@code storeId} / {@code customerId}）
     * @return 总数 + 当页订单
     */
    @PostMapping("/order/page")
    TradeOrderPageVO pagePlatformOrders(@RequestBody TradeOrderPlatformPageQueryDTO dto);

    /**
     * 平台侧订单详情（全量视角）
     *
     * @param orderNo 业务可读单号
     * @return 订单
     * @throws com.panoramic.common.exception.ServiceException 订单不存在（HTTP 404）
     */
    @GetMapping("/order/{orderNo}")
    TradeOrderVO getPlatformOrder(@PathVariable("orderNo") String orderNo);
}
