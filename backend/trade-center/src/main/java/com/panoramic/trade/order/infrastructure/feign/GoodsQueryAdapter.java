package com.panoramic.trade.order.infrastructure.feign;

import com.panoramic.common.feign.DomainResp;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.SpecAttr;
import com.panoramic.contract.store.dto.StoreGoodsSkuBatchQueryDTO;
import com.panoramic.contract.store.vo.StoreGoodsSkuSnapshotVO;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.SkuSnapshot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link GoodsQueryPort} 的真实实现：经 {@code StoreClient} 调 store 域的**交易侧 SKU 快照批量读**
 * （{@code POST /internal/store/goods/trade/sku/batch}），把出参逐字段映射成本域的只读投影
 * {@link SkuSnapshot}。
 *
 * <h3>为什么逐字段手工映射，不用 {@code BeanUtils.copyProperties}</h3>
 * <p>两边的类型是**刻意不同源**的：{@code StoreGoodsSkuSnapshotVO} 给的是各维度的**原始取值**
 * （{@code shopStatus=2} / {@code skuShelfStatus=1} / {@code lockStatus=0}…），本域的 {@code SkuSnapshot}
 * 要的是**四个开关**——「哪个取值算通过」是一次解释，解释必须写在看得见的地方。整对象拷贝会把
 * 「取值」直接灌进「开关」的位置（类型上还都是 {@code Integer}/{@code Boolean} 之外的巧合撑不住），
 * 且新增字段时静默漏映射，编译期一声不响。</p>
 *
 * <h3>为什么它不吞异常、不做降级</h3>
 * <p>与端 BFF 的方向**相反**：BFF 的下游失败要降级成「暂不可用」让页面能渲染，而域间调用是
 * 下单链路的一环——store 不可达 / 熔断打开时**下单必须整体失败**（异常一路抛到编排器，
 * 订单不落库），绝不能吞掉异常继续建单——那会产出「没扣库存的订单」（超卖）。
 * 故本类**不用** {@code BffFeignCall}。见 cross-cutting 第 24 条。</p>
 *
 * <h3>缺失语义</h3>
 * <p>查不到的 skuId（SKU 或所属 SPU 已删除）store 域**跳过不返**，本类照端口的契约把它翻译成
 * 「不在 map 里」——端口只如实回话，「缺失算不算错误」由调用方（goods-check / 拆单）决定。
 * 入参为空集合时**不发起 Feign 调用**直接返空表：下游 DTO 的 {@code skuIds} 带 {@code @NotEmpty}，
 * 空集合换来的是一个 400，而「没查」与「没查到」在这里是同一件事。</p>
 */
public class GoodsQueryAdapter implements GoodsQueryPort {

    /** {@code store_shop.status} = 已通过（0草稿，1待审核，2已通过，3已驳回） */
    private static final int SHOP_STATUS_APPROVED = 2;

    /** {@code shelf_status} / {@code lock_status} 的取值（0下架/未锁定，1上架/已锁定） */
    private static final int FLAG_ON = 1;

    private final StoreClient storeClient;

    public GoodsQueryAdapter(StoreClient storeClient) {
        this.storeClient = storeClient;
    }

    @Override
    public Map<Long, SkuSnapshot> mapBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        // ⚠ DomainResp.unwrap 而非 BffFeignCall：解包后域侧业务失败（code≠200）**照旧抛异常**，
        //   由编排器让整次下单失败——域间调用不能降级（见类注释与 cross-cutting 第 24 条）。
        List<StoreGoodsSkuSnapshotVO> rows = DomainResp.unwrap(storeClient.tradeSkuSnapshotBatch(queryOf(skuIds)));
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        // LinkedHashMap：遍历顺序 = 下游返回顺序，排查时同一批入参的产出稳定可读
        Map<Long, SkuSnapshot> snapshots = new LinkedHashMap<>();
        for (StoreGoodsSkuSnapshotVO row : rows) {
            if (row == null || row.getSkuId() == null) {
                continue;   // 缺失 id 不进 map（端口契约：查不到就是没有，不塞 null 值）
            }
            snapshots.put(row.getSkuId(), toSnapshot(row));
        }
        return snapshots;
    }

    /**
     * 组装批量查询入参
     *
     * <p>⚠ 用可变副本而不是 {@code List.copyOf}：后者遇到 null 元素会直接 NPE，
     * 而「入参里混了个 null」应该以「查不到」收场，不该在适配器里炸成 500。</p>
     */
    private static StoreGoodsSkuBatchQueryDTO queryOf(Collection<Long> skuIds) {
        StoreGoodsSkuBatchQueryDTO query = new StoreGoodsSkuBatchQueryDTO();
        query.setSkuIds(new ArrayList<>(skuIds));
        return query;
    }

    /**
     * 快照行 → 本域只读投影：把 store 的**取值**解释成本域的**四开关**
     *
     * <p>四个开关分属四个事实（店铺审核态、SPU 上下架、SKU 上下架、平台锁定），goods-check 的
     * 四条拒绝分支各依赖其一——故逐条映射，不做任何「合成一个 boolean」的简化（合成之后就再也
     * 说不清「为什么不可购买」）。取值取不到（{@code null}）一律按「否」：{@code null} 不是任何一种
     * 通过态，把它当成通过等于让缺失态变成可购买。</p>
     */
    private static SkuSnapshot toSnapshot(StoreGoodsSkuSnapshotVO row) {
        return new SkuSnapshot(
                row.getSpuId(),
                row.getSkuId(),
                row.getStoreId(),
                row.getStoreName(),
                row.getSpuName(),
                row.getMainImage(),
                toSpecAttrs(row.getSpecAttrs()),
                row.getPrice(),
                row.getShopStatus() != null && row.getShopStatus() == SHOP_STATUS_APPROVED,
                row.getSpuShelfStatus() != null && row.getSpuShelfStatus() == FLAG_ON,
                row.getSkuShelfStatus() != null && row.getSkuShelfStatus() == FLAG_ON,
                row.getLockStatus() != null && row.getLockStatus() == FLAG_ON);
    }

    /**
     * 规格列表 → 规格表：{@code [{"spec":"颜色","value":"黑色"}]} → {@code {"颜色":"黑色"}}
     *
     * <p>键取属性的**名称列**（{@code spec}）。名称空白或取值为 null 的元素丢掉——
     * 空键会造出一个谁也点不开的规格项，而它在下单链路里没有任何用处。</p>
     */
    private static Map<String, String> toSpecAttrs(List<SpecAttr> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (SpecAttr attr : attrs) {
            if (attr == null || attr.getSpec() == null || attr.getSpec().isBlank() || attr.getValue() == null) {
                continue;
            }
            result.put(attr.getSpec(), attr.getValue());
        }
        return result;
    }
}
