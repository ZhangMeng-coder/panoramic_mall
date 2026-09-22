package com.panoramic.trade.service.impl;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 购物车**六个写**路径的锚点护栏：作用域为 {@code null} 一律 400（{@code ScopeGuard}）。
 *
 * <p>⚠ 守的是哪条默认路径：DTO 上的 {@code @NotNull} 只在 MVC 边界生效；绕过它的调用把 {@code null}
 * 送到 SQL 层后，条件 {@code customer_id = ?} 直接**不入 SQL**（「不限定」），
 * 于是「按行 id 改一行」变成「按行 id 改**任意一行**」——不报错、只写坏别人的数据。
 * ⚠ 六个写**无例外**（含失败形态温和的 {@code clearCart}）：留一条不过护栏的写，
 * 「写侧必经 ScopeGuard」这句就成了不成立的话。对照的两个读（列表 / 计数）作用域缺失即「不限定」，
 * 且由 {@code @RequestParam} 必填 + 域异常处理器兜住，不在此列。</p>
 *
 * <p>⚠ 本类不启 Spring：护栏在方法第一句，{@code baseMapper} 与缓存一个都不会被碰到，
 * 故依赖传 {@code null} 即可——这也正是要断言的「没走到任何真实读写」。</p>
 */
class TradeCartItemServiceImplScopeGuardTest {

    private TradeCartItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TradeCartItemServiceImpl(null);
    }

    @Test
    @DisplayName("加购缺 customerId → 400")
    void addItemWithoutScopeIsRejected() {
        TradeCartItemAddDTO dto = new TradeCartItemAddDTO();
        dto.setSpuId(1L);
        dto.setSkuId(2L);
        dto.setQuantity(1);

        assertThatThrownBy(() -> service.addItem(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");
    }

    @Test
    @DisplayName("改数量缺 customerId → 400（不是「按行 id 改任意一行」）")
    void updateQuantityWithoutScopeIsRejected() {
        TradeCartItemUpdateDTO dto = new TradeCartItemUpdateDTO();
        dto.setQuantity(2);

        assertThatThrownBy(() -> service.updateQuantity(1L, dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");
    }

    @Test
    @DisplayName("改选中缺 customerId → 400")
    void setItemSelectedWithoutScopeIsRejected() {
        TradeCartSelectDTO dto = new TradeCartSelectDTO();
        dto.setSelected(true);

        assertThatThrownBy(() -> service.setItemSelected(1L, dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");
    }

    @Test
    @DisplayName("全选缺 customerId → 400（否则就是整表改）")
    void setAllSelectedWithoutScopeIsRejected() {
        TradeCartSelectDTO dto = new TradeCartSelectDTO();
        dto.setSelected(false);

        assertThatThrownBy(() -> service.setAllSelected(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");
    }

    @Test
    @DisplayName("批量删缺 customerId → 400（否则就是删任意行）")
    void removeItemsWithoutScopeIsRejected() {
        TradeCartItemIdsDTO dto = new TradeCartItemIdsDTO();
        dto.setIds(List.of(1L, 2L));

        assertThatThrownBy(() -> service.removeItems(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");
    }

    @Test
    @DisplayName("清空缺 customerId → 400（失败形态虽温和，写路径无例外）")
    void clearCartWithoutScopeIsRejected() {
        assertThatThrownBy(() -> service.clearCart(null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缺少顾客 id");
    }
}
