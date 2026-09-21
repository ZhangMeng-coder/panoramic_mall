package com.panoramic.contract.trade.api;

import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import com.panoramic.contract.trade.vo.TradeCartItemVO;
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
 * <p>交易域本期只持「购物车 {@code trade_cart_item}」（订单 / 结账 / 评价不在本期，见仓库根 {@code todo.md}），
 * 不向页面暴露公网路由，只被 mall-bff 经本接口内部调用。规约（见 CLAUDE.md 与 docs/contracts/trade-center.md）：
 * <ul>
 *   <li>入参/出参 DTO 与接口同源维护在 trade-center-interface（域服务端、mall-bff 客户端引用同一份类型）；</li>
 *   <li>方法直接返回业务结果类型（不包 RespData），错误走异常统一传播；</li>
 *   <li>调用经 {@link TradeFeignConfiguration} 附带信任头 + 透传主身份 + 错误解码；熔断由<b>调用方</b>经 Nacos
 *       {@code feign-circuitbreaker.yml} 配置提供，不在本类。</li>
 * </ul>
 * 数据权限口径：锚点 {@code customerId} = {@code mall_user.id}（跨域 id 引用、无外键），所有方法全按传入锚点过滤；
 * <b>无 owner / platform 分侧</b>——本期只做 C 端自助，调用方传的 {@code customerId} 是否「本人」由 mall-bff
 * 从登录态取，域内不做任何身份判断（不读 X-User-Type 判权；该头只用于审计留痕）。
 * 服务端路径与映射需与 trade-center 域内部控制器一一对应（前缀 /internal/trade，类级再拼 /cart）。
 * <p>⚠ <b>字段定义不在契约表里</b>：字段即 {@code com.panoramic.contract.trade.dto} / {@code .vo} 包下的类，
 * 契约表（docs/contracts/trade-center.md）只登记「有哪些接口、形状是什么、类型在哪、谁在调」，**不抄字段**
 * （抄一份就是制造第二个会漂移的地方）。</p>
 * <p>⚠ 方法<b>按行分批补齐</b>：摘掉契约表某行的 {@code 待实现} 标记、在此声明该方法、域侧补上实现，
 * 三者必须落在同一个提交里（否则 drift-check 的标记腐烂反向哨兵会报错）。
 * 当前购物车 8 条（列表 / 计数 / 加购 / 改数量 / 单行选中 / 全选 / 批量删除 / 清空）**全部落地**
 * （契约表 {@code 待实现} 归零）。</p>
 */
@FeignClient(name = "trade-center", contextId = "tradeCenterClient",
        path = "/internal/trade", configuration = TradeFeignConfiguration.class)
public interface TradeCenterClient {

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
}
