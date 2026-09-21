package com.panoramic.trade.order.infrastructure.inmemory;

import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.SkuSnapshot;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link GoodsQueryPort} 的内存实现（阶段一的临时下游：真实商品数据源是 store 域，阶段二接）。
 *
 * <p>⚠ <b>临时脚手架</b>：本包（`infrastructure/inmemory`）随 T4b 一起删除（todo 残留 9）——
 * 届时商品 / 库存改由 store 域的真实适配器提供，单测改用 `src/test` 下的轻量假实现。
 * 之所以现在还在 `main` 而不是 `test`：阶段一要**起真实服务打接口**，
 * 它得作为「商品 / 库存来自哪」的临时答案参与装配（R20）。</p>
 *
 * <p>⚠ 它同时是**测试夹具的落点**：商品不可购买的四个开关（店铺未审核 / SPU 未上架 / SKU 未上架 / 被平台锁定）
 * 分属四个不同事实，goods-check 的拒绝分支必须在四个开关上各验一条，故这里提供
 * {@link #sellable} 与四个「只翻转一个开关」的工厂：从可购买的基线出发改一个开关，
 * 测试用例才看得出自己到底在验哪一条分支，而不是对着 12 个参数的构造器数位置。</p>
 *
 * <p>⚠ 为什么不像真实实现那样按 id 逐个查：端口要的是「批量 map」，本类直接把预置好的表按 key 取子集，
 * 于是「查不到的 id 不在 map 里」这条契约天然成立——这也是 goods-check 能区分「不存在」与「不可购买」的前提。</p>
 */
public class InMemoryGoodsQueryPort implements GoodsQueryPort {

    /** skuId → 快照（预置表；用 {@code LinkedHashMap} 让遍历顺序可预期，便于排查） */
    private final Map<Long, SkuSnapshot> snapshots = new LinkedHashMap<>();

    /**
     * 预置 / 覆盖一个 SKU 快照（测试入口）
     *
     * @param snapshot 快照（其 {@code skuId} 即索引键）
     */
    public synchronized void put(SkuSnapshot snapshot) {
        snapshots.put(snapshot.skuId(), snapshot);
    }

    /**
     * 批量预置（测试入口）
     *
     * @param snapshots 快照集合
     */
    public synchronized void putAll(Collection<SkuSnapshot> snapshots) {
        snapshots.forEach(this::put);
    }

    /**
     * 移除一个 SKU（测试入口）——用于构造「商品不存在」这一条分支
     *
     * @param skuId 店铺 SKU id
     */
    public synchronized void remove(Long skuId) {
        snapshots.remove(skuId);
    }

    /**
     * 清空预置（测试入口：每个用例从空表开始，避免用例之间互相残留）
     */
    public synchronized void clear() {
        snapshots.clear();
    }

    @Override
    public synchronized Map<Long, SkuSnapshot> mapBySkuIds(Collection<Long> skuIds) {
        Map<Long, SkuSnapshot> found = new HashMap<>();
        if (skuIds == null) {
            return found;
        }
        for (Long skuId : skuIds) {
            SkuSnapshot snapshot = snapshots.get(skuId);
            if (snapshot != null) {
                found.put(skuId, snapshot);
            }
        }
        return found;
    }

    // ── 预置快照的工厂：可购买基线 + 四个「只坏一个开关」的变体（测试夹具） ─────────────────

    /**
     * 基线快照：店铺已审核、SPU 与 SKU 都已上架、未被锁定 —— 即**可以购买**
     *
     * @param skuId     店铺 SKU id
     * @param storeId   所属店铺 id
     * @param storeName 店铺名
     * @param price     单价（字符串形式，避免二进制浮点误差）
     * @return 四开关全部放行的快照
     */
    public static SkuSnapshot sellable(Long skuId, Long storeId, String storeName, String price) {
        return snapshot(skuId, storeId, storeName, price, true, true, true, false);
    }

    /**
     * 店铺未审核通过（goods-check 的第一条拒绝分支）
     *
     * @return 仅 {@code shopApproved=false} 的快照
     */
    public static SkuSnapshot shopNotApproved(Long skuId, Long storeId, String storeName, String price) {
        return snapshot(skuId, storeId, storeName, price, false, true, true, false);
    }

    /**
     * SPU 已下架（第二条拒绝分支）
     *
     * @return 仅 {@code spuOnShelf=false} 的快照
     */
    public static SkuSnapshot spuOffShelf(Long skuId, Long storeId, String storeName, String price) {
        return snapshot(skuId, storeId, storeName, price, true, false, true, false);
    }

    /**
     * SKU 已下架（第三条拒绝分支）
     *
     * @return 仅 {@code skuOnShelf=false} 的快照
     */
    public static SkuSnapshot skuOffShelf(Long skuId, Long storeId, String storeName, String price) {
        return snapshot(skuId, storeId, storeName, price, true, true, false, false);
    }

    /**
     * 被平台锁定（第四条拒绝分支）
     *
     * <p>锁定与「店主自己下架」是两件事：锁定是平台行为（整行只读、名下 SKU 已级联下架），
     * 排查时该找平台而不是找店主，故它是一个独立的开关。</p>
     *
     * @return 仅 {@code spuLocked=true} 的快照
     */
    public static SkuSnapshot locked(Long skuId, Long storeId, String storeName, String price) {
        return snapshot(skuId, storeId, storeName, price, true, true, true, true);
    }

    private static SkuSnapshot snapshot(Long skuId, Long storeId, String storeName, String price,
                                        boolean shopApproved, boolean spuOnShelf, boolean skuOnShelf, boolean spuLocked) {
        return new SkuSnapshot(skuId + 1000L, skuId, storeId, storeName, "商品" + skuId,
                "http://img/" + skuId + ".png", Map.of("颜色", "黑"), new BigDecimal(price),
                shopApproved, spuOnShelf, skuOnShelf, spuLocked);
    }
}
