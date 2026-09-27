package com.panoramic.trade.order.infrastructure.feign;

import com.panoramic.common.feign.DomainResp;
import com.panoramic.contract.store.api.StoreClient;
import com.panoramic.contract.store.dto.StoreGoodsSalesItemDTO;
import com.panoramic.contract.store.dto.StoreGoodsSalesPushDTO;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.SalesPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link SalesPort} 的真实实现：经 {@code StoreClient} 把销量推给 store 域的台账（
 * {@code POST /internal/store/goods/trade/sales/{orderNo}}）。
 *
 * <p>本类只做一件事——<b>把「行视角」翻译成「SPU 视角」</b>：订单明细是**一行一个 SKU**，
 * 而 store 域的销量口径是**一个 SPU 一行合计件数**（同一 SPU 下的多个 SKU 不各推一次，
 * 否则同一个 {@code (orderNo, spuId)} 会撞对方台账的唯一键，整条推送失败）。
 * 聚合放在这里而不是端口或应用服务里：它是**契约形状**要求的翻译（{@code StoreGoodsSalesItemDTO}
 * 把「按 spuId 聚合」明确划给调用方），与订单域自己的模型无关。</p>
 *
 * <p>⚠ <b>不吞异常、不做降级</b>（见 cross-cutting 第 24 条）：store 不可达 / 熔断打开 / 对方业务失败，
 * 异常一律原样上抛，由调用处（监听器 / 兜底任务）决定「只 warn 还是记 error」。
 * ⚠ 本类**不能**改成「catch 了记日志就返回」：那样「推失败」与「推成功」在上层长得一模一样，
 * 兜底扫描的补推依据（{@code sales_pushed} 标记）就再也标不实了。</p>
 *
 * <p>⚠ 为什么用 {@code DomainResp.unwrap} 而不是 {@code BffFeignCall}：本类是**域间**调用
 * （第 24 条），不解包成降级文案——store 侧 {@code code=400/404} 要作为业务异常上抛，只有
 * {@code code=200} 才取 {@code data}。</p>
 */
public class SalesAdapter implements SalesPort {

    private final StoreClient storeClient;

    public SalesAdapter(StoreClient storeClient) {
        this.storeClient = storeClient;
    }

    @Override
    public void pushSales(OrderModel order) {
        StoreGoodsSalesPushDTO dto = new StoreGoodsSalesPushDTO();
        dto.setItems(aggregateBySpu(order));
        // 顺序 = 明细顺序（LinkedHashMap 保序）：同一个订单每次推出去的报文逐字节一样，
        // 排查「补推推了什么」时不必再看一次顺序差异
        DomainResp.unwrap(storeClient.addSalesByOrder(order.getOrderNo(), dto));
    }

    /**
     * 订单明细 → SPU 合计件数（**按 SPU 合并**，保留首次出现顺序）
     *
     * @param order 待推送的订单
     * @return 每个 SPU 一行；订单没有明细时抛 {@link IllegalStateException}
     * @throws IllegalStateException 订单没有任何可推的明细（数据被写坏）
     */
    private static List<StoreGoodsSalesItemDTO> aggregateBySpu(OrderModel order) {
        Map<Long, Integer> quantityBySpuId = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            // 明细的 spuId / quantity 是下单时冻结的快照（OrderItem#rehydrate 已对账），
            // 故这里不做空值防御——为空即数据被写坏，让它以 NPE 之外的形态显式失败
            if (item.getSpuId() == null || item.getQuantity() <= 0) {
                throw new IllegalStateException("订单 " + order.getOrderNo() + " 的明细 spuId=" + item.getSpuId()
                        + " 件数=" + item.getQuantity() + " 不合法，不能推销量");
            }
            quantityBySpuId.merge(item.getSpuId(), item.getQuantity(), Integer::sum);
        }
        if (quantityBySpuId.isEmpty()) {
            // 订单必有明细（下单要求至少一行），走到这里说明库里那张单被写坏了。
            // ⚠ 不能「空就跳过」了事：那会让 sales_pushed 被标记成已推 —— 一笔销量永久丢失，
            //    而兜底扫描再也不会重捞它
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 没有任何明细，不能推销量");
        }
        List<StoreGoodsSalesItemDTO> items = new ArrayList<>(quantityBySpuId.size());
        quantityBySpuId.forEach((spuId, quantity) -> {
            StoreGoodsSalesItemDTO item = new StoreGoodsSalesItemDTO();
            item.setSpuId(spuId);
            item.setQuantity(quantity);
            items.add(item);
        });
        return items;
    }
}
