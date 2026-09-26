package com.panoramic.trade.controller;

import com.panoramic.common.vo.RespData;
import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import com.panoramic.contract.trade.vo.TradeCartItemVO;
import com.panoramic.trade.service.TradeCartItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车内部领域接口（trade-center 域下沉纯域）。
 * <p>仅供端 BFF（mall-bff，C 端顾客自助）经内部 Feign（{@code /internal/trade/cart/...}）调用，
 * 不向页面暴露公网路由。出参一律包 {@code RespData<T>}（cross-cutting 第 2 条）：业务结果（含业务失败，
 * 如「行不存在 / 不归属」）走 HTTP 200 + {@code code}，异常由 common 的 {@code GlobalExceptionHandler}
 * 兜底成 HTTP 500 —— 那是熔断唯一的失败信号（第 13 条）。</p>
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：数据作用域 {@code customerId} 由调用方携带——
 * <b>不进路径段</b>（cross-cutting 第 22 条）：只有这一个非路径入参的三条（列表 / 计数 / 清空）用裸
 * {@code @RequestParam}，其余五条把它放进各自 DTO 的字段（第 23 条）。它是否等于「本人」由 mall-bff
 * 从登录态取，域侧不校验（防线在 BFF）。身份头只由
 * {@code TradeUserIdentityFilter} 填 {@code UserContext} 供审计留痕，缺头即留空、不回 401。</p>
 * <p>⚠ 出参就是 {@code TradeCartItemVO}（原始行），<b>不含商品信息、不含可见性判定</b>：
 * 商品实时详情由 mall-bff 另行批量取 store 域，可见性也在那里重判（见
 * {@code docs/contracts/trade-center.md} 第一节）。</p>
 */
@RestController
@RequestMapping("/internal/trade/cart")
@RequiredArgsConstructor
public class CartController {

    private final TradeCartItemService tradeCartItemService;

    /**
     * 我的购物车行列表（按加购顺序）；空购物车返回空列表
     */
    @GetMapping
    public RespData<List<TradeCartItemVO>> listCartItems(@RequestParam("customerId") Long customerId) {
        return RespData.success(tradeCartItemService.listItems(customerId));
    }

    /**
     * 购物车行数（角标用；行数 ≠ 件数）
     */
    @GetMapping("/count")
    public RespData<Integer> cartItemCount(@RequestParam("customerId") Long customerId) {
        return RespData.success(tradeCartItemService.countItems(customerId));
    }

    /**
     * 加入购物车（同一 SKU 累加数量），返回该 SKU 对应的行 id
     */
    @PostMapping("/items")
    public RespData<Long> addCartItem(@Validated @RequestBody TradeCartItemAddDTO dto) {
        return RespData.success(tradeCartItemService.addItem(dto));
    }

    /**
     * 修改单行数量（整份覆盖；行不存在/不归属 → {@code code=404}）
     */
    @PutMapping("/items/{id}")
    public RespData<Void> updateCartItemQuantity(@PathVariable("id") Long id,
                                                 @Validated @RequestBody TradeCartItemUpdateDTO dto) {
        tradeCartItemService.updateQuantity(id, dto);
        return RespData.success();
    }

    /**
     * 设置单行选中状态（行不存在/不归属 → {@code code=404}）
     */
    @PutMapping("/items/{id}/selected")
    public RespData<Void> setCartItemSelected(@PathVariable("id") Long id,
                                              @Validated @RequestBody TradeCartSelectDTO dto) {
        tradeCartItemService.setItemSelected(id, dto);
        return RespData.success();
    }

    /**
     * 全选 / 全不选（作用域内的整表，幂等）
     */
    @PutMapping("/selected")
    public RespData<Void> setAllCartItemsSelected(@Validated @RequestBody TradeCartSelectDTO dto) {
        tradeCartItemService.setAllSelected(dto);
        return RespData.success();
    }

    /**
     * 批量删除购物车行（物理删除；幂等）
     */
    @PostMapping("/items/remove")
    public RespData<Void> removeCartItems(@Validated @RequestBody TradeCartItemIdsDTO dto) {
        tradeCartItemService.removeItems(dto);
        return RespData.success();
    }

    /**
     * 清空购物车（物理删除；幂等）
     */
    @DeleteMapping
    public RespData<Void> clearCart(@RequestParam("customerId") Long customerId) {
        tradeCartItemService.clearCart(customerId);
        return RespData.success();
    }
}
