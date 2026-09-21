package com.panoramic.trade.order.application.step;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.OrderCreateStep;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.GoodsQueryPort;
import com.panoramic.trade.order.domain.port.SkuSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 计价步骤（{@code price-compute}）：把单价写进订单行、汇总总金额，并**封存订单**
 * （todo 原话：「根据商品价格和数量计算总价」）。
 *
 * <p>⚠ <b>为什么再查一次商品</b>（goods-check 刚查过）：步骤之间只经 {@link OrderModel} 传参（todo 原话），
 * 商品快照里只有展示字段，**价格刻意没有留在模型上**——顾客能改包改的是入参，价格必须每次由服务端从
 * 商品域读。这多一次跨域查询是**刻意的取舍**（裁定 D9）：让每一步自洽、不依赖前置步骤的非模型产出，
 * 比省一次查询重要（真实实现下这次查询与 goods-check 那次可以合并成一次批量调用，但那是适配器的优化，
 * 不该由步骤之间的隐式约定来承担）。</p>
 *
 * <p>⚠ <b>金额一律由模型自己算</b>：单价交给 {@link OrderModel#applyPrice} 后，小计与总额由聚合根派生，
 * 本步骤不碰 {@code BigDecimal} 的乘加——金额算式有唯一落点，才不会出现「这里算一遍、那边又算一遍」。</p>
 *
 * <p>⚠ <b>最后一步 {@link OrderModel#seal()}</b>：它在封存前对账「行快照齐备 + 总额等于行小计之和」。
 * 这一步是整条流水线的**总闸门**：步骤少跑一个、顺序调错、金额算歪，都会在这里被拦住，
 * 而不是等落库后才发现。⚠ 它的 {@code IllegalStateException} **不要 catch**：那是装配/编程错误，
 * 不是顾客输入问题，吞掉它等于把配置错误伪装成一笔成功订单。</p>
 */
@Component
public class PriceComputeStep implements OrderCreateStep {

    /** 步骤名：与 {@code panoramic.trade.order.steps} 里写的名字是同一个契约 */
    public static final String NAME = "price-compute";

    private final GoodsQueryPort goodsQueryPort;

    public PriceComputeStep(GoodsQueryPort goodsQueryPort) {
        this.goodsQueryPort = goodsQueryPort;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 逐行定价、汇总金额并封存
     *
     * @param order 已补齐商品快照的订单（未 seal）
     * @throws ServiceException 商品查不到（口径与 goods-check 一致：不在 map 里即「不存在」）
     */
    @Override
    public void execute(OrderModel order) {
        List<Long> skuIds = order.getItems().stream().map(OrderItem::getSkuId).toList();
        Map<Long, SkuSnapshot> snapshots = goodsQueryPort.mapBySkuIds(skuIds);
        for (OrderItem item : order.getItems()) {
            SkuSnapshot snapshot = snapshots.get(item.getSkuId());
            if (snapshot == null) {
                // 本步骤自洽：不假设 goods-check 一定跑过（步骤可插拔、可换顺序），缺失就按同口径报错
                throw new ServiceException(400, "商品不存在");
            }
            order.applyPrice(item.getSkuId(), snapshot.price());
        }
        order.seal();
    }
}
