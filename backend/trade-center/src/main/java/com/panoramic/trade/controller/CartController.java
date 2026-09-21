package com.panoramic.trade.controller;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车内部领域接口（trade-center 域下沉纯域）。
 * <p>仅供端 BFF（mall-bff，C 端顾客自助）经内部 Feign（{@code /internal/trade/cart/...}）调用，
 * 不向页面暴露公网路由；方法直接返回业务原类型（不包 RespData），错误经
 * {@code TradeDomainExceptionHandler} 以真实 HTTP 状态码传播。</p>
 * <p><b>域内不做任何鉴权、不做权限判断、不校验 token</b>：路径上的 {@code customerId} 就是数据权限锚点，
 * 它是否等于「本人」由 mall-bff 从登录态取，域侧不校验（防线在 BFF）。身份头只由
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
    @GetMapping("/{customerId}")
    public List<TradeCartItemVO> listCartItems(@PathVariable("customerId") Long customerId) {
        return tradeCartItemService.listItems(customerId);
    }

    /**
     * 购物车行数（角标用；行数 ≠ 件数）
     */
    @GetMapping("/{customerId}/count")
    public Integer cartItemCount(@PathVariable("customerId") Long customerId) {
        return tradeCartItemService.countItems(customerId);
    }

    /**
     * 加入购物车（同一 SKU 累加数量），返回该 SKU 对应的行 id
     */
    @PostMapping("/{customerId}/items")
    public Long addCartItem(@PathVariable("customerId") Long customerId,
                            @Validated @RequestBody TradeCartItemAddDTO dto) {
        return tradeCartItemService.addItem(customerId, dto);
    }

    /**
     * 修改单行数量（整份覆盖；行不存在/不归属 → 404）
     */
    @PutMapping("/{customerId}/items/{id}")
    public void updateCartItemQuantity(@PathVariable("customerId") Long customerId,
                                       @PathVariable("id") Long id,
                                       @Validated @RequestBody TradeCartItemUpdateDTO dto) {
        tradeCartItemService.updateQuantity(customerId, id, dto);
    }

    /**
     * 设置单行选中状态（行不存在/不归属 → 404）
     */
    @PutMapping("/{customerId}/items/{id}/selected")
    public void setCartItemSelected(@PathVariable("customerId") Long customerId,
                                    @PathVariable("id") Long id,
                                    @Validated @RequestBody TradeCartSelectDTO dto) {
        tradeCartItemService.setItemSelected(customerId, id, dto);
    }

    /**
     * 全选 / 全不选（整表，幂等）
     */
    @PutMapping("/{customerId}/selected")
    public void setAllCartItemsSelected(@PathVariable("customerId") Long customerId,
                                        @Validated @RequestBody TradeCartSelectDTO dto) {
        tradeCartItemService.setAllSelected(customerId, dto);
    }

    /**
     * 批量删除购物车行（物理删除；幂等）
     */
    @PostMapping("/{customerId}/items/remove")
    public void removeCartItems(@PathVariable("customerId") Long customerId,
                                @Validated @RequestBody TradeCartItemIdsDTO dto) {
        tradeCartItemService.removeItems(customerId, dto);
    }

    /**
     * 清空购物车（物理删除；幂等）
     */
    @DeleteMapping("/{customerId}")
    public void clearCart(@PathVariable("customerId") Long customerId) {
        tradeCartItemService.clearCart(customerId);
    }
}
