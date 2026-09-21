package com.panoramic.trade.order.application;

import com.panoramic.common.exception.ServiceException;
import com.panoramic.trade.order.domain.OrderItem;
import com.panoramic.trade.order.domain.OrderSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 下单入参（application 层入口类型）：**一次提交 = 一批商品行**，拆单由编排层完成。
 *
 * <p>⚠ 为什么入参里没有店铺、没有价格、没有金额：店铺由商品归属推出（一单一店，裁定 D3），
 * 价格必须由服务端从商品域读（顾客改包就能改价的那类字段一律不进 DTO）。入参只保留
 * 「谁、从哪来、买什么、买几件、以及一个幂等键」。</p>
 *
 * <p>⚠ <b>为什么在这里就把同一 skuId 的行合并（裁定 D14）</b>：聚合根 {@code OrderModel.open} 明确拒绝
 * 「同一 skuId 出现多行」（多行会让 {@code applyPrice(skuId, ...)} 指不明确，属静默写坏数据）。
 * 而购物车入参天然可能出现重复行（同一 SKU 被多个入口/标签页选中、或调用方未先去重），
 * 把它当错误抛给顾客是不合理的——「买 2 件」与「分两行各买 1 件」在业务上是同一件事。
 * 于是合并**必须在命令层完成**：这是唯一知道「重复行原本长什么样」的地方，
 * 进了聚合根就只剩「要么拒绝、要么静默只改一行」两种错答案。</p>
 *
 * <p>⚠ 合并**按 skuId 升序**输出：入参顺序不可控（购物车选中行顺序可变），排序后同一批商品
 * 无论怎么传都得到同一个命令对象，下游的指纹、拆单、订单行排序才有确定性。</p>
 *
 * <p>⚠ 校验用 {@link ServiceException}(400) 而不是 {@link IllegalArgumentException}：这里的每个拒绝
 * 都是**顾客输入问题**（空车、数量越界），该原样透传到页面（4xx 透传见 cross-cutting 第 13 条）；
 * 装配错误才用 {@code IllegalStateException}（裁定 D12）。</p>
 *
 * @param customerId 顾客 id（锚点，由 mall-bff 从登录态取；域内不校验归属）
 * @param source     订单来源（详情页直购 / 购物车结算），是指纹的敏感维度之一
 * @param requestId  请求级幂等键（裁定 D6 第一级）；可为 null——为空表示这次提交不做请求级去重
 * @param lines      商品行（**已按 skuId 合并、升序**；合并后为空即拒绝）
 */
public record OrderCreateCommand(Long customerId, OrderSource source, String requestId, List<Line> lines) {

    /**
     * 紧凑构造器：合并 + 校验（D14），保证任何一个 {@code OrderCreateCommand} 实例都自洽
     */
    public OrderCreateCommand {
        Objects.requireNonNull(customerId, "下单入参的顾客 id 不能为空");
        Objects.requireNonNull(source, "下单入参的订单来源不能为空");
        lines = mergeAndValidate(lines);
    }

    /**
     * 下单行（application 层类型）
     *
     * <p>⚠ 它与 domain 的 {@code OrderLine} 是**两个类型**，刻意不合并：domain 零依赖、不能引
     * application 层的类型（裁定 D11），两者字段相同属可接受的重复——类型归属比省一个 record 重要。
     * 由编排层在开单时逐行翻译成 {@code OrderLine}。</p>
     *
     * @param skuId    店铺 SKU id
     * @param quantity 购买数量（**合并后的**数量，构造命令时由 {@link OrderCreateCommand} 校验）
     */
    public record Line(Long skuId, int quantity) {

        public Line {
            Objects.requireNonNull(skuId, "下单行的 skuId 不能为空");
        }
    }

    /**
     * 合并同一 skuId 的数量并校验取值区间（D14）
     *
     * <p>用 {@code long} 累加是刻意的：行数不设上限时 int 求和可能溢出成负数，
     * 那会让「数量越界」变成一个静默通过的负数——校验就白做了。</p>
     *
     * @param incoming 原始入参行（可为 null）
     * @return 合并后按 skuId 升序排列的不可变行列表
     * @throws ServiceException 合并后没有任何行（空车），或某行数量不在 {@code 1..999}
     */
    private static List<Line> mergeAndValidate(List<Line> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            throw new ServiceException(400, "下单商品不能为空");
        }
        // TreeMap：合并的同时就把 skuId 升序定下来，不必再排一次
        Map<Long, Long> merged = new TreeMap<>();
        for (Line line : incoming) {
            Objects.requireNonNull(line, "下单行不能为空");
            merged.merge(line.skuId(), (long) line.quantity(), Long::sum);
        }
        List<Line> result = new ArrayList<>(merged.size());
        for (Map.Entry<Long, Long> entry : merged.entrySet()) {
            long quantity = entry.getValue();
            // 上限口径与 domain 的 OrderItem 同源（不另立一个 999），避免两条入口出现两个上限
            if (quantity < OrderItem.MIN_QUANTITY || quantity > OrderItem.MAX_QUANTITY) {
                throw new ServiceException(400, "商品数量必须在 " + OrderItem.MIN_QUANTITY + ".."
                        + OrderItem.MAX_QUANTITY + " 之间（skuId=" + entry.getKey() + "，合并后为 " + quantity + "）");
            }
            result.add(new Line(entry.getKey(), (int) quantity));
        }
        if (result.isEmpty()) {
            throw new ServiceException(400, "下单商品不能为空");
        }
        return List.copyOf(result);
    }
}
