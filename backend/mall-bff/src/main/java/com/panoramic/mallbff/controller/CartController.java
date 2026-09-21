package com.panoramic.mallbff.controller;

import com.panoramic.common.util.UserContext;
import com.panoramic.common.vo.RespData;
import com.panoramic.mallbff.dto.MallCartItemAddDTO;
import com.panoramic.mallbff.dto.MallCartItemIdsDTO;
import com.panoramic.mallbff.dto.MallCartItemUpdateDTO;
import com.panoramic.mallbff.dto.MallCartSelectDTO;
import com.panoramic.mallbff.service.CartBffService;
import com.panoramic.mallbff.vo.MallCartVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端购物车接口（列表 / 徽标计数 / 加购 / 改数量 / 选中 / 删除 / 清空，共 8 条）。
 *
 * <p><b>形状</b>：本类只把购物车行「加上商品信息」后按店铺分组下发（商品名 / 图 / 规格 / 价格 / 库存
 * 由 store 域批量详情补齐，见 {@link CartBffService}）；行本身（数量 / 选中态）属 trade-center，
 * 服务端持久化——刷新页面不丢选中态。</p>
 *
 * <p>⚠ <b>顾客 id 只能取自 {@code UserContext}</b>（登录态），绝不从请求体 / 路径接收：
 * 域内不做任何鉴权（{@code customerId} 就是数据权限本身），BFF 是唯一授权点；
 * 域侧每条读写都带 {@code customer_id = ?} 条件，故拿不到别人的行。</p>
 *
 * <p>分层：本类只碰 {@link CartBffService}，<b>不注入</b>任何 Feign 客户端，
 * 页面类型 ↔ 域契约类型的映射收在该 service 内。</p>
 *
 * <p>⚠ <b>C 端不接 RBAC</b>：本类没有、也不应有任何 {@code @PreAuthorize}——顾客对自己的购物车全权限。
 * 本类全部路径<b>均需登录态</b>（登记在 {@code application.yml} 的「需登录态端点」注），
 * <b>不得</b>加进任何免鉴权白名单（首页免鉴权白名单只有 {@code /catalog/categories} 一条精确路径）。</p>
 *
 * <p><b>错误形状</b>：下游业务 4xx 原样透传——「购物车行不存在」404、「数量/行数超上限」400 如实回页面；
 * 只有下游故障才降级为 500「购物车暂不可用，请稍后重试」。
 * ⚠ <b>加购</b>是唯一在 BFF 侧先判的商品前置（不可见 → 400「该商品已下架或不可购买」），
 * 但<b>不校验库存</b>：购物车是购买意向不是占位。</p>
 *
 * <p>⚠ <b>口径差异不是 bug</b>：{@code GET /cart/count} 出的是<b>行数</b>（轻口径，不做可见性判定，
 * 顶栏每页都能刷新），{@code GET /cart} 的 {@code totalQuantity} 是<b>有效行件数之和</b>。
 * 另：购物车里「已失效」的行<b>不从列表里删掉</b>，只标 {@code invalid} 并排除在合计之外。</p>
 */
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartBffService cartBffService;

    /**
     * 我的购物车（按店铺分组 + 汇总；空车时 {@code shops} 为空列表、汇总全 0）
     */
    @GetMapping
    public RespData<MallCartVO> cart() {
        return RespData.success(cartBffService.cart(UserContext.getUserId()));
    }

    /**
     * 购物车徽标计数（行数，顶栏用；不做可见性判定，见类注释的口径差异）
     */
    @GetMapping("/count")
    public RespData<Integer> count() {
        return RespData.success(cartBffService.count(UserContext.getUserId()));
    }

    /**
     * 加入购物车（同一 SPU + SKU 已存在则<b>累加数量</b>，不新增行；单行上限 999、单车上限 100 行）
     *
     * @return 该行 id
     */
    @PostMapping("/items")
    public RespData<Long> addItem(@Valid @RequestBody MallCartItemAddDTO dto) {
        return RespData.success(cartBffService.addItem(UserContext.getUserId(), dto));
    }

    /**
     * 改数量（绝对值，不是增量）
     */
    @PutMapping("/items/{id}")
    public RespData<Void> updateQuantity(@PathVariable("id") Long id,
                                         @Valid @RequestBody MallCartItemUpdateDTO dto) {
        cartBffService.updateQuantity(UserContext.getUserId(), id, dto);
        return RespData.success();
    }

    /**
     * 改单行选中态
     */
    @PutMapping("/items/{id}/selected")
    public RespData<Void> setItemSelected(@PathVariable("id") Long id,
                                          @Valid @RequestBody MallCartSelectDTO dto) {
        cartBffService.setItemSelected(UserContext.getUserId(), id, dto);
        return RespData.success();
    }

    /**
     * 全选 / 全不选（⚠ 域侧整表操作，<b>含已失效行</b>；页面上的「全选」勾选态按有效行推导）
     */
    @PutMapping("/selected")
    public RespData<Void> setAllSelected(@Valid @RequestBody MallCartSelectDTO dto) {
        cartBffService.setAllSelected(UserContext.getUserId(), dto);
        return RespData.success();
    }

    /**
     * 批量删除（选中删除 / 删除单行 / 删除失效行都走它；⚠ <b>幂等</b>：行不存在不算失败）
     */
    @PostMapping("/items/remove")
    public RespData<Void> removeItems(@Valid @RequestBody MallCartItemIdsDTO dto) {
        cartBffService.removeItems(UserContext.getUserId(), dto);
        return RespData.success();
    }

    /**
     * 清空购物车（⚠ <b>幂等</b>：空车调用也成功）
     */
    @DeleteMapping
    public RespData<Void> clear() {
        cartBffService.clear(UserContext.getUserId());
        return RespData.success();
    }
}
