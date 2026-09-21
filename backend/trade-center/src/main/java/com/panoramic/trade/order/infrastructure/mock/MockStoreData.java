package com.panoramic.trade.order.infrastructure.mock;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code resources/mock-store-data.json} 的形状（阶段一：store 域的**只读快照**）。
 *
 * <p>⚠ <b>它是脚手架的一部分，随 T4b 一起删除</b>（todo 残留 9）。</p>
 *
 * <p>字段刻意保留 store 域表的**原始语义**（{@code shopStatus} 是 {@code store_shop.status} 的取值、
 * {@code spuShelfStatus} 是 {@code store_goods_spu.shelf_status} 的取值……），而不是直接存布尔开关：
 * 「哪个取值算审核通过」是适配器的解释，把它写死在导出脚本里，解释就搬到了一个没人评审的地方
 * （一次性 Node 脚本），而且将来真实适配器要用另一套判据时，两份解释谁对也说不清。
 * 于是：JSON 只搬事实，解释留在 {@link MockStoreConfiguration}。</p>
 *
 * @param exportedAt 导出时间（排查用：数据久了会过时，尤其是库存）
 * @param source     导出来源（写明是哪张表 / 哪个库，避免以后不知道这份数据从哪来）
 * @param note       这份数据与真实库的**已知差异**（尤其是 {@code stock}：它是 mock 的初值，
 *                   不是导出的事实——写明差异比让后来者自己猜「为什么库里是 0 这里是 50」强）
 * @param skus       快照行（一行 = 一个 SKU；已按「店铺 + SPU + SKU」连好表）
 */
public record MockStoreData(String exportedAt, String source, String note, List<Sku> skus) {

    /**
     * 紧凑构造器：空数据一律启动失败
     *
     * <p>⚠ 空数据不是「无商品」而是「这份快照坏了」：静默接受的话，每一次下单都会以「商品不存在」
     * 被拒，而报错指向顾客的入参——真正的原因（文件没打好 / 路径写错）要查很久。</p>
     */
    public MockStoreData {
        if (skus == null || skus.isEmpty()) {
            throw new IllegalStateException("mock 商品数据为空：请检查 resources/mock-store-data.json 是否导出成功");
        }
        skus = List.copyOf(skus);
    }

    /**
     * 一个 SKU 的快照行
     *
     * @param skuId          店铺 SKU id（{@code store_goods_sku.id}）
     * @param spuId          店铺 SPU id（{@code store_goods_spu.id}）
     * @param storeId        所属店铺 id（{@code store_shop.id}，即店主账号 id）
     * @param storeName      店铺名（{@code store_shop.shop_name}）
     * @param shopStatus     店铺审核状态（{@code store_shop.status}：0草稿，1待审核，2已通过，3已驳回）
     * @param goodsName      SPU 名称（{@code store_goods_spu.goods_name}）
     * @param mainImage      SPU 主图（{@code store_goods_spu.main_image}，可为 null）
     * @param specAttrs      SKU 规格（{@code store_goods_sku.spec_attrs} 的 JSON 反序列化结果；
     *                       库里是**数组** {@code [{"spec":"颜色","value":"黑色"},…]} 而非对象，
     *                       故这里保持 {@link SpecAttr} 列表原形，转成 {@code Map} 是适配器的解释）
     * @param price          SKU 售价（{@code store_goods_sku.price}）
     * @param stock          库存**初值**（内存脚手架以它起账，之后不再回写；⚠ 它不声称等于
     *                       {@code store_goods_sku_stock.stock}，差异见 {@code note}）
     * @param spuShelfStatus SPU 上下架（{@code store_goods_spu.shelf_status}：0下架，1上架）
     * @param skuShelfStatus SKU 上下架（{@code store_goods_sku.shelf_status}：0下架，1上架）
     * @param spuLockStatus  SPU 平台锁定态（{@code store_goods_spu.lock_status}：0未锁定，1已锁定）
     */
    public record Sku(Long skuId,
                      Long spuId,
                      Long storeId,
                      String storeName,
                      Integer shopStatus,
                      String goodsName,
                      String mainImage,
                      List<SpecAttr> specAttrs,
                      BigDecimal price,
                      Integer stock,
                      Integer spuShelfStatus,
                      Integer skuShelfStatus,
                      Integer spuLockStatus) {
    }

    /**
     * 一条规格键值（{@code store_goods_sku.spec_attrs} 数组里的一个元素）
     *
     * <p>与 store 域的 DDL 注释同义：{@code spec} 是规格名（如「颜色」）、{@code value} 是取值（如「黑色」）。</p>
     */
    public record SpecAttr(String spec, String value) {
    }
}
