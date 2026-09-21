package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 订单状态机：**顺序来自配置**，域内只认「下标 +1」（裁定 D7）。
 *
 * <p>todo 的原话是「订单状态也需要根据配置信息或数据库配置来设置每一步的流转状态」。
 * 落法是把顺序写成一个有序列表（{@code panoramic.trade.order.status-flow}），本类只做三件事：
 * 建起来时校验配置合法、给一个「下一个状态」、以及拦住一切不是「下标 +1」的迁移。
 * 于是「加一个状态」= 改配置 + 加枚举常量，**没有任何 if/else 需要回头改**。</p>
 *
 * <p>⚠ 构造期就 fail fast：配置缺枚举常量 / 有重复 / 为空一律 {@link IllegalStateException}（装配失败），
 * 而不是等到某次迁移才炸。缺常量的后果是「那个状态永远到不了」——它不报错，只会让某笔订单卡死，
 * 属于最难查的一类问题，所以必须让服务起不来。</p>
 *
 * <p>⚠ 两类错误刻意用两种异常：<b>配置/装配错</b>（缺常量、重复、空）是程序员错误 → {@code IllegalStateException}；
 * <b>用户可见的非法迁移</b>（跳级 / 回退 / 重复变更）是业务错误 → {@code ServiceException(400)}，
 * 且提示用两侧的 mallLabel 拼成可直接展示的中文（裁定 D12）。</p>
 *
 * <p>⚠ 本期**不做**取消 / 超时关单（裁定 D8）：那是「不按线性顺序走」的状态，真要做得给本类加前驱集合，
 * 不能靠往线性列表里塞一个常量硬凑。</p>
 */
public final class OrderStatusFlow {

    /** 配置的流转顺序（下标即先后） */
    private final List<OrderStatus> statuses;

    /**
     * 由配置的有序列表构建状态机
     *
     * @param statuses 状态流转顺序（数组顺序即先后）
     * @throws IllegalStateException 列表为空、含 null、有重复，或未覆盖 {@link OrderStatus} 的全部常量
     */
    public OrderStatusFlow(List<OrderStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            throw new IllegalStateException("订单状态流转配置不能为空（panoramic.trade.order.status-flow 至少要有初始状态）");
        }
        List<OrderStatus> configured = new ArrayList<>(statuses.size());
        for (OrderStatus status : statuses) {
            if (status == null) {
                throw new IllegalStateException("订单状态流转配置里出现空值（status-flow 只允许写状态常量名）");
            }
            if (configured.contains(status)) {
                throw new IllegalStateException("订单状态流转配置里出现重复状态：" + status.name()
                        + "（重复会让「下标 +1」的判据失效）");
            }
            configured.add(status);
        }
        Set<OrderStatus> missing = EnumSet.allOf(OrderStatus.class);
        missing.removeAll(configured);
        if (!missing.isEmpty()) {
            List<String> names = missing.stream().map(Enum::name).toList();
            throw new IllegalStateException("订单状态流转配置缺少状态：" + names
                    + "（缺一个就有一个状态永远到不了，故装配期直接失败）");
        }
        this.statuses = List.copyOf(configured);
    }

    /**
     * 取当前状态的下一个合法状态
     *
     * @param current 当前状态
     * @return 下一个状态
     * @throws ServiceException         当前状态已是最后一个（用户可读：订单已完结）
     * @throws IllegalArgumentException 当前状态不在本状态机的配置里（配置已断言覆盖全部常量，故这是编程错误）
     */
    public OrderStatus next(OrderStatus current) {
        int index = indexOf(current);
        if (index == statuses.size() - 1) {
            throw new ServiceException(400, "订单已处于最后一个状态「" + current.getMallLabel() + "」，无法继续推进");
        }
        return statuses.get(index + 1);
    }

    /**
     * 校验一次迁移是否合法：**只允许下标 +1**（不跨级、不回退、不重复）
     *
     * <p>提示语带上迁移前后的两侧 mallLabel，是因为这条异常最终会经 BFF 透传给用户
     * （4xx 原样透传，见 cross-cutting 第 13 条）——用户看到的应该是「订单状态不能从「待发货」回退到「已支付」」，
     * 而不是一个状态枚举名。</p>
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws ServiceException         HTTP 400，非法迁移
     * @throws IllegalArgumentException from / to 为 null，或 from 不在配置里（编程错误）
     */
    public void assertCanTransition(OrderStatus from, OrderStatus to) {
        Objects.requireNonNull(to, "目标订单状态不能为空");
        int fromIndex = indexOf(from);
        int toIndex = indexOf(to);
        if (toIndex == fromIndex + 1) {
            return;
        }
        String move = toIndex < fromIndex ? "回退" : (toIndex == fromIndex ? "重复变更" : "跳级");
        throw new ServiceException(400, "订单状态不能从「" + from.getMallLabel() + "」" + move + "到「" + to.getMallLabel() + "」");
    }

    /**
     * 在订单上执行一次状态迁移（校验通过后由模型自己改状态并留轨迹）
     *
     * <p>⚠ 未 seal 的订单在这里就被拦下：一笔还没补全的订单不能是「待支付」（裁定见 {@link OrderModel}）。
     * 本类的检查与 {@link OrderModel#applyStatus} 里的检查是**刻意重复**的——入口有两个，
     * 异常类型不能因走哪个入口而不同（装配顺序错就该是 {@code IllegalStateException}，
     * 不该因为先撞上迁移校验而变成 400 业务错）。</p>
     *
     * @param order  目标订单
     * @param target 目标状态
     * @throws IllegalStateException 订单尚未 seal
     * @throws ServiceException      非法迁移（跳级 / 回退 / 重复变更），HTTP 400
     */
    public void transition(OrderModel order, OrderStatus target) {
        Objects.requireNonNull(order, "订单不能为空");
        if (!order.isSealed()) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 尚未封存（seal），不能迁移状态");
        }
        assertCanTransition(order.getStatus(), target);
        order.applyStatus(target);
    }

    /**
     * @return 配置的流转顺序（**不可变副本**）——供装配层的配置漂移校验比对枚举常量集合
     */
    public List<OrderStatus> statuses() {
        return List.copyOf(statuses);
    }

    private int indexOf(OrderStatus status) {
        Objects.requireNonNull(status, "订单状态不能为空");
        int index = statuses.indexOf(status);
        if (index < 0) {
            throw new IllegalArgumentException("订单状态 " + status.name() + " 不在订单状态流转配置里");
        }
        return index;
    }
}
