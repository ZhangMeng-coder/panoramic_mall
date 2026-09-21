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
 * 商品校验步骤（{@code goods-check}）：把「这单能不能买」判掉，并把商品快照冻结进订单行。
 *
 * <p>它承担两件事，且只有这两件：</p>
 * <ol>
 *   <li><b>校验</b>——SKU 存在、店铺已审核、SPU 已上架、SKU 已上架、未被平台锁定；</li>
 *   <li><b>补快照</b>——逐行 {@link OrderModel#applyGoodsSnapshot}，把商品名 / 主图 / 规格冻进订单项
 *       （裁定 D15：之后不再回查商品，商品改名换图都不影响已有订单）。</li>
 * </ol>
 *
 * <p>⚠ <b>价格不归本步骤</b>：定价是 {@code price-compute} 的事（todo 把「判断商品状态」与「计算总价」分成两步）。
 * 本步骤只回答「这东西此刻可不可以卖」，即使快照里带着价格也不去写它——一步一件事，
 * 才不会出现「改定价口径要动校验步骤」这种歪依赖。</p>
 *
 * <p>⚠ <b>四个开关分开判、不合成一个 boolean</b>（见 {@code SkuSnapshot}）：它们分属不同事实
 * （店铺审核态 / SPU 上下架 / SKU 上下架 / 平台锁定），合成一个就再也说不清「为什么不可购买」。
 * 对顾客只给一句可读的原因，但四个判据在代码里各占一行，将来要分流（例如锁定走客服、未审核走店主）
 * 得改的只是提示语的映射。</p>
 *
 * <p>⚠ 查不到（不在返回的 map 里）与「查得到但不可购买」给**两种**提示：前者是「商品不存在」
 * （多半是前端拿着过期的 skuId），后者是「商品已下架或不可购买」——把两种合成一句，顾客会去问
 * 「为什么下架了」，而实际是商品根本不存在。</p>
 */
@Component
public class GoodsCheckStep implements OrderCreateStep {

    /** 步骤名：与 {@code panoramic.trade.order.steps} 里写的名字是同一个契约 */
    public static final String NAME = "goods-check";

    private final GoodsQueryPort goodsQueryPort;

    public GoodsCheckStep(GoodsQueryPort goodsQueryPort) {
        this.goodsQueryPort = goodsQueryPort;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 逐行校验并补全商品快照
     *
     * @param order 待校验的订单（未 seal）
     * @throws ServiceException 商品不存在，或商品不可购买（HTTP 400，可原样透传页面）
     */
    @Override
    public void execute(OrderModel order) {
        List<Long> skuIds = order.getItems().stream().map(OrderItem::getSkuId).toList();
        Map<Long, SkuSnapshot> snapshots = goodsQueryPort.mapBySkuIds(skuIds);
        for (OrderItem item : order.getItems()) {
            Long skuId = item.getSkuId();
            SkuSnapshot snapshot = snapshots.get(skuId);
            if (snapshot == null) {
                throw new ServiceException(400, "商品不存在");
            }
            if (!snapshot.shopApproved() || !snapshot.spuOnShelf() || !snapshot.skuOnShelf() || snapshot.spuLocked()) {
                throw new ServiceException(400, "商品已下架或不可购买");
            }
            order.applyGoodsSnapshot(skuId, snapshot);
        }
    }
}
