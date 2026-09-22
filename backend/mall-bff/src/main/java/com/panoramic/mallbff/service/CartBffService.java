package com.panoramic.mallbff.service;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.common.feign.BffFeignCall;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.StoreGoodsSpuBatchQueryDTO;
import com.panoramic.contract.store.vo.StoreGoodsSkuVO;
import com.panoramic.contract.store.vo.StoreGoodsSpuPlatformDetailVO;
import com.panoramic.contract.trade.api.TradeCenterClient;
import com.panoramic.contract.trade.dto.TradeCartItemAddDTO;
import com.panoramic.contract.trade.dto.TradeCartItemIdsDTO;
import com.panoramic.contract.trade.dto.TradeCartItemUpdateDTO;
import com.panoramic.contract.trade.dto.TradeCartSelectDTO;
import com.panoramic.contract.trade.vo.TradeCartItemVO;
import com.panoramic.mallbff.dto.MallCartItemAddDTO;
import com.panoramic.mallbff.dto.MallCartItemIdsDTO;
import com.panoramic.mallbff.dto.MallCartItemUpdateDTO;
import com.panoramic.mallbff.dto.MallCartSelectDTO;
import com.panoramic.mallbff.vo.MallCartItemVO;
import com.panoramic.mallbff.vo.MallCartShopVO;
import com.panoramic.mallbff.vo.MallCartVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * C 端购物车编排（列表 / 计数 / 加购 / 改数量 / 选中 / 删除 / 清空）。
 *
 * <p><b>两层归属</b>：购物车行本身（顾客、数量、选中态）属 <b>trade-center</b>，本层只做
 * 「取行 → 补商品信息 → 判可见性 → 按店铺分组 → 汇总」的页面编排；商品名 / 图 / 规格 / 价格 / 库存
 * 属 <b>store 域</b>，域不持购物车，本层也不复制任何域的表。</p>
 *
 * <p><b>读路径不产生 N+1</b>：一次 {@code listCartItems} 取回全部行，再一次
 * {@code platformSpuBatch} 取回这些 SPU 的详情（保底 4 条 SQL、与行数无关），
 * 然后<b>在内存里</b>按行补信息。⚠ 逐行调详情是 N+1，禁止。</p>
 *
 * <p><b>可见性不变量在本层重判</b>（docs/contracts/cross-cutting.md 第 20 条）：域返回的是
 * <b>管理端超集</b>——下架 / 被锁定 / 店铺未过审的 SPU 一样查得到。故「这一行还算不算可买」由本层
 * 用 {@link CatalogBffService#visibleSpuIds} 判定（与商品详情<b>同一处判定代码</b>，不另写一份），
 * 判为不可见的行打 {@code invalid} 标记后<b>照常下发</b>（顾客要看得见才敢删它），只是不进件数与金额。</p>
 *
 * <p><b>写路径直透</b>：改数量 / 改选中 / 删除 / 清空都是「用户点了就生效」的原子操作，本层不二次判定、
 * 不补偿，一律经 {@link BffFeignCall} 调 trade-center；只有<b>加购</b>多一步「商品对 C 端是否可见」的前置校验
 * （不可见就不该落行）。下游业务 4xx（如「购物车行不存在」404、超上限 400）原样透传给页面，
 * 其余降级为「购物车暂不可用，请稍后重试」。</p>
 *
 * <p>⚠ <b>数量上限与库存的分工</b>：单行 999 / 单车 100 行是<b>域侧</b>的硬上限（超限 400 透传）；
 * 库存<b>只影响展示</b>（{@code purchasable}），加购与改数量都不校验、不锁定库存——
 * 购物车是购买意向不是占位（见 docs/contracts/mall-bff.md「加购不校验库存」）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartBffService {

    /** trade-center 熔断/连接异常降级提示 */
    private static final String TRADE_DOWN_MSG = "购物车暂不可用，请稍后重试";
    /** store 域熔断/连接异常降级提示（与商品详情同一句，出口文案保持一致） */
    private static final String STORE_DOWN_MSG = "商品暂不可用，请稍后重试";

    /** 商品已删（连店铺归属都拿不到）时的兜底分组名 */
    private static final String ORPHAN_SHOP_NAME = "已失效商品";

    /** C 端「不可见」统一文案（与商品详情同口径：不存在 / 已下架 / 被锁定 / 店铺未过审 一律不区分） */
    private static final String NOT_VISIBLE_MSG = "该商品已下架或不可购买";

    private final TradeCenterClient tradeCenterClient;
    private final StoreClient storeClient;
    private final CatalogBffService catalogBffService;

    /**
     * 我的购物车：取行 → 批量补商品信息 → 逐行判可见性 → 按店铺分组 → 汇总。
     *
     * @param customerId 顾客账号 id（<b>只能取自登录态</b>，见 controller）
     * @return 购物车（空车时 shops 为空列表、汇总全 0）
     */
    public MallCartVO cart(Long customerId) {
        List<TradeCartItemVO> rows = BffFeignCall.call("trade-center", TRADE_DOWN_MSG,
                () -> tradeCenterClient.listCartItems(customerId));
        if (rows == null || rows.isEmpty()) {
            return emptyCart();
        }
        Map<Long, StoreGoodsSpuPlatformDetailVO> details = batchDetails(rows);
        // 可见性判定复用商品详情那一处（同一条不变量），批量判定内部按去重后的店铺数取店铺状态
        Set<Long> visibleSpuIds = catalogBffService.visibleSpuIds(details.values());

        // 分组用 LinkedHashMap：保持域侧返回的行顺序（加购先后），分组顺序 = 首次出现顺序
        Map<Long, MallCartShopVO> grouped = new LinkedHashMap<>();
        // SPU 已物理删除的行连 storeId 都拿不到，单独收在兜底分组里，排在最后
        List<MallCartItemVO> orphanItems = new ArrayList<>();
        int totalQuantity = 0;
        int selectedQuantity = 0;
        int invalidCount = 0;
        BigDecimal selectedAmount = BigDecimal.ZERO;

        for (TradeCartItemVO row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            StoreGoodsSpuPlatformDetailVO detail = details.get(row.getSpuId());
            MallCartItemVO item = toMallItem(row, detail, visibleSpuIds.contains(row.getSpuId()));
            if (Boolean.TRUE.equals(item.getInvalid())) {
                invalidCount++;
            } else {
                int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
                totalQuantity += quantity;
                // 金额只算「有效且选中」的行（失效行绝不能进合计，否则页面会报出一个买不到的总额）
                if (Boolean.TRUE.equals(item.getSelected())) {
                    selectedQuantity += quantity;
                    if (item.getPrice() != null) {
                        selectedAmount = selectedAmount.add(
                                item.getPrice().multiply(BigDecimal.valueOf(quantity)));
                    }
                }
            }
            if (detail == null || detail.getStoreId() == null) {
                orphanItems.add(item);
                continue;
            }
            MallCartShopVO shop = grouped.computeIfAbsent(detail.getStoreId(), storeId -> {
                MallCartShopVO vo = new MallCartShopVO();
                vo.setShopId(storeId);
                // storeName 由域按 SPU 填（域持 store_shop）；域没填时留空串，前端按空值不渲染店铺行
                vo.setShopName(detail.getStoreName() == null ? "" : detail.getStoreName());
                vo.setItems(new ArrayList<>());
                return vo;
            });
            shop.getItems().add(item);
        }

        if (!orphanItems.isEmpty()) {
            MallCartShopVO orphan = new MallCartShopVO();
            orphan.setShopId(null);
            orphan.setShopName(ORPHAN_SHOP_NAME);
            orphan.setItems(orphanItems);
            grouped.put(ORPHAN_SHOP_KEY, orphan);
        }

        MallCartVO vo = new MallCartVO();
        vo.setShops(new ArrayList<>(grouped.values()));
        vo.setTotalQuantity(totalQuantity);
        vo.setSelectedQuantity(selectedQuantity);
        // 两位小数 HALF_UP：金额只在本层算一次下发（前端不再乘加一遍，两份就会漂）
        vo.setSelectedAmount(selectedAmount.setScale(2, RoundingMode.HALF_UP));
        vo.setInvalidCount(invalidCount);
        return vo;
    }

    /**
     * 购物车徽标计数（顶栏用）。
     * <p>⚠ 口径是<b>行数</b>、且<b>不做可见性判定</b>（域侧 {@code count(*)} + Redis 读穿透）——
     * 每个页面都能廉价刷新。与 {@link MallCartVO#getTotalQuantity()}（有效行件数之和）<b>口径不同，不是 bug</b>，
     * 见 docs/contracts/mall-bff.md「徽标口径」。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @return 购物车行数
     */
    public Integer count(Long customerId) {
        Integer count = BffFeignCall.call("trade-center", TRADE_DOWN_MSG,
                () -> tradeCenterClient.cartItemCount(customerId));
        return count == null ? 0 : count;
    }

    /**
     * 加入购物车。
     * <p>落行前只校一件事：<b>该商品对 C 端可见</b>（店铺已审核 + SPU 上架 + 未锁定），
     * 不可见即 400 中文提示、不落行；<b>不校验库存、不锁库存</b>（购物车是购买意向不是占位）。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param dto        商品 / 规格 / 数量
     * @return 购物车行 id（新加入或已存在那行的 id）
     */
    public Long addItem(Long customerId, MallCartItemAddDTO dto) {
        StoreGoodsSpuPlatformDetailVO detail = catalogBffService.visibleDetailOrNull(dto.getSpuId());
        if (detail == null) {
            throw new ServiceException(NOT_VISIBLE_MSG);
        }
        // 规格必须属于这个 SPU 且在售：id 是别家 SPU 的、或该 SKU 已下架（SPU 上架 ≠ 所有 SKU 都在售），
        // 一律拒。⚠ 这道校验不能在域侧做——域不持商品信息。
        StoreGoodsSkuVO sku = CatalogBffService.findSku(detail, dto.getSkuId());
        if (sku == null || !CatalogBffService.SHELF_ON.equals(sku.getShelfStatus())) {
            throw new ServiceException("该规格已下架，请重新选择");
        }
        TradeCartItemAddDTO payload = new TradeCartItemAddDTO();
        payload.setCustomerId(customerId);
        payload.setSpuId(dto.getSpuId());
        payload.setSkuId(dto.getSkuId());
        payload.setQuantity(dto.getQuantity());
        return BffFeignCall.call("trade-center", TRADE_DOWN_MSG,
                () -> tradeCenterClient.addCartItem(payload));
    }

    /**
     * 改数量（绝对值，服务端持久化）。
     * <p>⚠ 不校验库存（见类注释）；行不存在或不属于本人 → 域侧 404，原样透传给页面。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param id         购物车行 id
     * @param dto        新数量
     */
    public void updateQuantity(Long customerId, Long id, MallCartItemUpdateDTO dto) {
        TradeCartItemUpdateDTO payload = new TradeCartItemUpdateDTO();
        payload.setCustomerId(customerId);
        payload.setQuantity(dto.getQuantity());
        BffFeignCall.call("trade-center", TRADE_DOWN_MSG, () -> {
            tradeCenterClient.updateCartItemQuantity(id, payload);
            return null;
        });
    }

    /**
     * 改单行选中态
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param id         购物车行 id
     * @param dto        选中态
     */
    public void setItemSelected(Long customerId, Long id, MallCartSelectDTO dto) {
        TradeCartSelectDTO payload = new TradeCartSelectDTO();
        payload.setCustomerId(customerId);
        payload.setSelected(dto.getSelected());
        BffFeignCall.call("trade-center", TRADE_DOWN_MSG, () -> {
            tradeCenterClient.setCartItemSelected(id, payload);
            return null;
        });
    }

    /**
     * 全选 / 全不选。
     * <p>⚠ 这是<b>域侧整表操作</b>（含已失效行），本层<b>不</b>改写成逐行调用——那是 N 次请求。
     * 页面上的「全选」勾选态按有效行推导，合计只算「有效且选中」。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param dto        选中态
     */
    public void setAllSelected(Long customerId, MallCartSelectDTO dto) {
        TradeCartSelectDTO payload = new TradeCartSelectDTO();
        payload.setCustomerId(customerId);
        payload.setSelected(dto.getSelected());
        BffFeignCall.call("trade-center", TRADE_DOWN_MSG, () -> {
            tradeCenterClient.setAllCartItemsSelected(payload);
            return null;
        });
    }

    /**
     * 批量删除（选中删除 / 删除单行 / 删除失效行共用）。
     * <p>域侧对「行不存在 / 不属于本人」是<b>幂等 no-op</b>：多选删除不该因某行被并发删掉而整体失败。</p>
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     * @param dto        待删除的行 id
     */
    public void removeItems(Long customerId, MallCartItemIdsDTO dto) {
        TradeCartItemIdsDTO payload = new TradeCartItemIdsDTO();
        payload.setCustomerId(customerId);
        payload.setIds(dto.getIds());
        BffFeignCall.call("trade-center", TRADE_DOWN_MSG, () -> {
            tradeCenterClient.removeCartItems(payload);
            return null;
        });
    }

    /**
     * 清空购物车（幂等：空车调用也成功）
     *
     * @param customerId 顾客账号 id（只能取自登录态）
     */
    public void clear(Long customerId) {
        BffFeignCall.call("trade-center", TRADE_DOWN_MSG, () -> {
            tradeCenterClient.clearCart(customerId);
            return null;
        });
    }

    // ---- 内部 ----

    /**
     * 兜底分组在 {@code grouped} 里的键。
     * <p>用 {@code 0L} 而不是 {@code null}：{@link java.util.LinkedHashMap} 允许 null 键，但一个
     * 「店铺 id = 0L」的键与「拿不到店铺」是两件事，混淆时不好查（店铺 id 是自增主键，不可能是 0）。</p>
     */
    private static final Long ORPHAN_SHOP_KEY = 0L;

    /**
     * 批量取跨店商品详情（<b>一次调用</b>，与行数无关）。
     * <p>⚠ 域侧<b>不做 C 端可见性过滤</b>（platform 侧出的是管理端超集），下架 / 锁定 / 未过审的
     * SPU 一样会返回；已<b>物理删除</b>的 SPU 则不在出参里——本层据「map 里有没有」区分
     * 「商品还在但不可买」与「商品已删」，页面文案不同。</p>
     *
     * @param rows 购物车行（非空）
     * @return spuId → 域详情
     */
    private Map<Long, StoreGoodsSpuPlatformDetailVO> batchDetails(List<TradeCartItemVO> rows) {
        List<Long> spuIds = rows.stream()
                .filter(Objects::nonNull)
                .map(TradeCartItemVO::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (spuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        StoreGoodsSpuBatchQueryDTO query = new StoreGoodsSpuBatchQueryDTO();
        query.setSpuIds(spuIds);
        List<StoreGoodsSpuPlatformDetailVO> details = BffFeignCall.call("store", STORE_DOWN_MSG,
                () -> storeClient.platformSpuBatch(query));
        Map<Long, StoreGoodsSpuPlatformDetailVO> result = new HashMap<>();
        if (details != null) {
            for (StoreGoodsSpuPlatformDetailVO detail : details) {
                if (detail != null && detail.getId() != null) {
                    result.put(detail.getId(), detail);
                }
            }
        }
        return result;
    }

    /**
     * 购物车行 + 域详情 → C 端购物车行（<b>逐字段手工映射</b>，不用 {@code BeanUtils.copyProperties}：
     * 域 VO 是管理端超集，含 {@code lockReason}/{@code lockUser}/{@code goodsSpuId}/{@code centerVersion} 等，
     * 域返回的字段不等于可以对外暴露）。
     *
     * @param row      域购物车行
     * @param detail   该 SPU 的域详情；{@code null} = SPU 已被物理删除
     * @param visible  该 SPU 是否通过 C 端可见性判定
     * @return C 端购物车行
     */
    private MallCartItemVO toMallItem(TradeCartItemVO row,
                                      StoreGoodsSpuPlatformDetailVO detail,
                                      boolean visible) {
        MallCartItemVO vo = new MallCartItemVO();
        vo.setId(row.getId());
        vo.setSpuId(row.getSpuId());
        vo.setSkuId(row.getSkuId());
        vo.setQuantity(row.getQuantity());
        vo.setSelected(row.getSelected());
        // SPU 已删：名称 / 图 / 规格 / 价格全为 null，availableStock 记 0（页面据此显示「商品已删除」占位）
        vo.setName(detail == null ? null : detail.getName());
        vo.setMainImage(detail == null ? null : detail.getMainImage());
        StoreGoodsSkuVO sku = detail == null ? null : CatalogBffService.findSku(detail, row.getSkuId());
        vo.setSpecAttrs(sku == null ? null : sku.getSpecAttrs());
        vo.setPrice(sku == null ? null : sku.getPrice());
        // 库存：SKU 拿不到按 0（与详情页同款兜底：本端出参契约是非可空整数）
        int availableStock = sku == null || sku.getAvailableStock() == null ? 0 : sku.getAvailableStock();
        vo.setAvailableStock(availableStock);
        // 失效 = SPU 不可见（不存在 / 下架 / 锁定 / 店铺未过审）或该 SKU 已下架
        // ⚠ 两个条件缺一不可：SPU 上架 ≠ 名下每个 SKU 都在售（不变量只保证「至少一个在售」）
        boolean skuOnShelf = sku != null && CatalogBffService.SHELF_ON.equals(sku.getShelfStatus());
        boolean invalid = !visible || !skuOnShelf;
        vo.setInvalid(invalid);
        int quantity = row.getQuantity() == null ? 0 : row.getQuantity();
        vo.setPurchasable(!invalid && quantity < availableStock);
        return vo;
    }

    /**
     * 空车出参（行数为 0 时直接返回，省掉一次 batch 调用；金额给 0.00 而不是 null）
     *
     * @return 空购物车
     */
    private MallCartVO emptyCart() {
        MallCartVO vo = new MallCartVO();
        vo.setShops(new ArrayList<>());
        vo.setTotalQuantity(0);
        vo.setSelectedQuantity(0);
        vo.setSelectedAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        vo.setInvalidCount(0);
        return vo;
    }
}
