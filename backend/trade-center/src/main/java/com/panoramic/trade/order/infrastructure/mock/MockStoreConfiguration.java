package com.panoramic.trade.order.infrastructure.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryGoodsQueryPort;
import com.panoramic.trade.order.infrastructure.inmemory.InMemoryStockPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段一的**商品 / 库存替身**：把 {@code resources/mock-store-data.json} 灌进两个内存端口。
 *
 * <p>⚠ <b>临时脚手架</b>：真实实现是 store 域的内部接口（阶段二接），届时本类、同包的
 * {@link MockStoreData} 与那个 JSON 文件、以及 {@code infrastructure/inmemory} 整个包一并删除
 * （todo 残留 9）。所以它宁可「够用就好」：不模拟并发、不模拟库存归还，只提供一份**真实形状**的商品数据。</p>
 *
 * <h3>为什么用「真实库的只读快照」而不是随手编几条假数据</h3>
 * <p>阶段一要验的是「下单编排 + 落库」这条链路，而链路的每一步都依赖商品事实：拆单按店铺分组
 * （于是必须有多家已审核店铺）、goods-check 的拒绝分支、库存不足（于是需要一个零库存的上架 SKU）。
 * 这些都是**库里的真实形态**，编出来的假数据只能验到「我想到的那几种情况」；用真数据则顺手把
 * 「真实数据长什么样」也一并验了。数据由个人 skill {@code mysql-connect} 从 {@code panoramic_mall}
 * 的 store 三张表只读导出（见 JSON 里的 {@code source} 字段），**不做任何写入**。</p>
 *
 * <p>⚠ <b>这份快照覆盖到哪、没覆盖到哪</b>（别把它当成「四条拒绝分支都验过了」）——goods-check 的
 * 四个条件是「或」，故一条反例行只钉得住「它单独坏掉的那一个开关」：</p>
 * <ul>
 *   <li>覆盖（各至少一行，且该行**只坏这一个开关**）：<b>SKU 下架</b>、<b>SPU 被平台锁定</b>、
 *       <b>上架但零库存</b>（可购买行里有一行 0 库存）、<b>多店拆单</b>（两家已审核店铺）。</li>
 *   <li>未覆盖：<b>店铺未审核</b>与<b>只坏「SPU 下架」这一个开关</b>——库里当前没有这样的行
 *       （反例行的 {@code store_shop.status} 都是 2，而唯一的 {@code spuShelfStatus=0} 行**同时**被平台锁定，
 *       从它看不出命中的是「SPU 下架」还是「锁定」）。这两条分支由单测的假快照覆盖
 *       （见 {@code OrderCreatePipelineTest}），<b>不靠这份快照</b>；将来谁发现某条分支只有单测在守，
 *       应该知道这是有意为之而非遗漏。</li>
 * </ul>
 *
 * <h3>为什么用开关 {@code panoramic.trade.order.store-adapter}</h3>
 * <p>它是「本服务当前把商品 / 库存指向谁」的显式声明：{@code mock} = 本类；阶段二把它改成
 * {@code feign}（真实 store 域）。</p>
 * <p>⚠ <b>阶段一 {@code mock} 这一侧带 {@code matchIfMissing = true}（= 键缺失时按 mock 装配）</b>，
 * 与「写错就起不来」并不矛盾：阶段二 T4b 落地时本类会被**删除**、默认值同时翻成 {@code feign}
 * （todo 残留 9）——那一刻起「键缺失」只会落到**唯一存在的那个**装配上。翻面之前，
 * 缺失值映射到的也只是「当前唯一存在的装配」，而写错一个值（{@code mockk} / 空串）仍会一个 bean 都不装配、
 * 启动即报「找不到 GoodsQueryPort / StockPort 的 bean」——起不来，而不是悄悄接单。</p>
 *
 * <p>⚠ 内存库存**只在本进程内扣减**，不回写 store 域：阶段一的库存是「一次运行内的账」，
 * 重启即回到快照值。这正是「库存归 store 域」这件事在阶段一的可见后果，不是 bug。</p>
 */
@Configuration
@ConditionalOnProperty(name = "panoramic.trade.order.store-adapter", havingValue = "mock", matchIfMissing = true)
public class MockStoreConfiguration {

    /** 快照文件（classpath 根下；与导出脚本产物同名，便于对照） */
    private static final String DATA_LOCATION = "mock-store-data.json";

    /** {@code store_shop.status} = 已通过（0草稿，1待审核，2已通过，3已驳回） */
    private static final int SHOP_STATUS_APPROVED = 2;

    /** {@code shelf_status} / {@code lock_status} 的取值（0下架/未锁定，1上架/已锁定） */
    private static final int FLAG_ON = 1;

    /**
     * 商品快照数据（读一次，两个端口 bean 共用；避免同一份文件解析两遍）
     *
     * @throws IllegalStateException 文件缺失 / 解析失败 / 数据为空（装配期即失败，不做降级）
     */
    @Bean
    public MockStoreData mockStoreData(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(DATA_LOCATION);
        if (!resource.exists()) {
            throw new IllegalStateException("找不到 mock 商品数据：" + DATA_LOCATION
                    + "（panoramic.trade.order.store-adapter=mock 时必须随包提供）");
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, MockStoreData.class);
        } catch (IOException e) {
            throw new IllegalStateException("mock 商品数据 " + DATA_LOCATION + " 解析失败：" + e.getMessage(), e);
        }
    }

    /**
     * 商品查询端口（快照 → {@link SkuSnapshot}：库里的取值在这里解释成四个开关）
     */
    @Bean
    public InMemoryGoodsQueryPort mockGoodsQueryPort(MockStoreData data) {
        InMemoryGoodsQueryPort port = new InMemoryGoodsQueryPort();
        for (MockStoreData.Sku sku : data.skus()) {
            port.put(toSnapshot(sku));
        }
        return port;
    }

    /**
     * 库存端口（以快照里的 {@code stock} 为初值，此后只在本进程内增减）
     */
    @Bean
    public InMemoryStockPort mockStockPort(MockStoreData data, Clock clock) {
        InMemoryStockPort port = new InMemoryStockPort(clock);
        for (MockStoreData.Sku sku : data.skus()) {
            port.setStock(sku.skuId(), sku.stock() == null ? 0 : sku.stock());
        }
        return port;
    }

    /**
     * 快照行 → 商品快照：把 store 域的**取值**翻译成本域的**四开关**
     *
     * <p>四个开关分属四个事实（店铺审核态、SPU 上下架、SKU 上下架、平台锁定），goods-check 的
     * 四条拒绝分支各依赖其一——所以这里逐条映射，不做任何「合成一个 boolean」的简化
     * （那会让「为什么不可购买」再也说不清，见 {@link SkuSnapshot} 的注释）。</p>
     */
    private static SkuSnapshot toSnapshot(MockStoreData.Sku sku) {
        return new SkuSnapshot(
                sku.spuId(),
                sku.skuId(),
                sku.storeId(),
                sku.storeName(),
                sku.goodsName(),
                sku.mainImage(),
                toSpecAttrs(sku.specAttrs()),
                sku.price(),
                sku.shopStatus() != null && sku.shopStatus() == SHOP_STATUS_APPROVED,
                sku.spuShelfStatus() != null && sku.spuShelfStatus() == FLAG_ON,
                sku.skuShelfStatus() != null && sku.skuShelfStatus() == FLAG_ON,
                sku.spuLockStatus() != null && sku.spuLockStatus() == FLAG_ON);
    }

    /**
     * 规格数组 → 规格表：{@code [{"spec":"颜色","value":"黑色"}]} → {@code {"颜色":"黑色"}}
     *
     * <p>为什么在这里转、不在 JSON 里就存成对象：列的原始形状是数组，转换是**解释**，
     * 解释留在适配器（同 {@link MockStoreData} 的类注释）。转换时丢掉规格名或取值为空白的元素——
     * 空键会造出一个谁也点不开的规格项，而它在下单链路里没有任何用处。</p>
     */
    private static Map<String, String> toSpecAttrs(List<MockStoreData.SpecAttr> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (MockStoreData.SpecAttr attr : attrs) {
            if (attr.spec() == null || attr.spec().isBlank() || attr.value() == null) {
                continue;
            }
            result.put(attr.spec(), attr.value());
        }
        return result;
    }
}
