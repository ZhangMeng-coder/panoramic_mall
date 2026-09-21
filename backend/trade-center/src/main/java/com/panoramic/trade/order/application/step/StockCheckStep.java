package com.panoramic.trade.order.application.step;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.application.OrderCreateStep;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderModel;
import com.panoramic.trade.order.domain.port.StockPort;
import org.springframework.stereotype.Component;

/**
 * 库存校验步骤（{@code stock-check}）：按订单行逐行扣库存，扣不动就整笔拒绝
 * （todo 原话：「判断商品库存是否足够…原子操作（判断出库数量是否足够，扣减库存数量并根据 SKU 的粒度创建出库记录）」）。
 *
 * <p>⚠ <b>「判断 + 扣减 + 写出库记录」三件事必须一次调用完成</b>，故本步骤只调
 * {@link StockPort#deduct}（它本身就是那个原子操作），而不是「先 available 问一句、够了再扣」——
 * 拆成两次调用会在中间留出被插队的窗口，超卖正是从这里进来的。本期内存实现用同步块保证原子性，
 * 将来真实实现对应的是 {@code UPDATE ... WHERE stock >= ?} 看受影响行数 + 同事务插入出库记录
 * （见 {@code StockPort} 的接口注释）。</p>
 *
 * <p>⚠ <b>扣减语义是扣 {@code stock}</b>（裁定 D2），{@code locked_stock} 已废弃、不参与本口径。</p>
 *
 * <p>⚠ <b>本步骤自己绝不回滚</b>：多行订单下完全可能出现「前几行已扣、后面某行不够」——
 * 这半成品状态**故意留在这里**，由编排层在整次提交失败时逆序回补（裁定 D4/D13）。
 * 若本步骤顺手把已扣的行还回去，编排层的回补就会**再还一遍**，库存被越冲越多——
 * 那是比少扣更难发现的错，因为它不会让任何一次下单失败。</p>
 */
@Component
public class StockCheckStep implements OrderCreateStep {

    /** 步骤名：与 {@code panoramic.trade.order.steps} 里写的名字是同一个契约 */
    public static final String NAME = "stock-check";

    private final StockPort stockPort;

    public StockCheckStep(StockPort stockPort) {
        this.stockPort = stockPort;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 逐行扣库存（按订单行顺序，首行不够即中断）
     *
     * @param order 待扣库存的订单（未 seal）
     * @throws ServiceException 库存不足（HTTP 400，可原样透传页面）
     */
    @Override
    public void execute(OrderModel order) {
        for (OrderItem item : order.getItems()) {
            boolean deducted = stockPort.deduct(item.getSkuId(), item.getQuantity(), order.getOrderNo());
            if (!deducted) {
                // 失败即中断：不够的那一行没扣、也没记录，前面已扣的留给编排层回补
                throw new ServiceException(400, "库存不足");
            }
        }
    }
}
