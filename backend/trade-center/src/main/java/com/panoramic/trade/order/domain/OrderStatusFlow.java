package com.panoramic.trade.order.domain;

import com.panoramic.common.exception.ServiceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单状态机：**一条线性主链（来自配置）+ 两个写死在代码里的结束过程**
 * （2026-09-24 起；此前是「每个状态声明它的前驱集合」的图）。
 *
 * <p>todo 的原话是「订单状态也需要根据配置信息或数据库配置来设置每一步的流转状态」。落法分两半：</p>
 * <ul>
 *   <li><b>主链</b>（待支付 → 已支付 → 已发货 → 已收货）来自配置
 *       {@code panoramic.trade.order.status-flow}，形状是**有序数组**：数组顺序即先后，相邻两项之间的边
 *       就是「推进一步」。这一半仍是配置驱动——加一个中间状态、调一次先后，只改配置。</li>
 *   <li><b>两个「结束过程」</b>（未支付 → 已取消、已支付 → 已退款）由**代码**写死：它们不是流程上可选的分支，
 *       而**就是那两个动作的定义**（「取消 = 把这笔待支付的单作废」这句话里已经含着「从待支付来」）。
 *       做成可配的只多出一个能配错的地方，而「灵活性」在这里没有意义——改它等于改业务定义，不是运维开关。</li>
 * </ul>
 *
 * <p>⚠ <b>谁来判哪一半</b>，这是本类最重要的一句话：</p>
 * <ul>
 *   <li>主链推进：{@link #advance}，判据是「目标必须是当前状态在配置里的下一个」——**配置是先后唯一的来源**；</li>
 *   <li>结束过程：{@link #endWith}，判据是「这笔单此刻必须停在**调用方（动作方法）声明的那个状态**」
 *       ——那个「从哪来」写在 {@link OrderModel#markCancelled} / {@link OrderModel#markRefunded} 里，本类不知道，
 *       也不该知道（旧的口径是把它当图的边写在本类，那正是被撤掉的东西）。</li>
 * </ul>
 *
 * <p>⚠ <b>本类只知道「哪些状态不在主链上」（{@link #ENDING_TARGETS}），不知道它们从哪来</b>。前者是必须的：
 * ① 配置里写了这两个状态得当场拒绝；② 轨迹对账要能认出「轨迹末尾那一个是结束过程收的尾」。
 * 而「从哪来」只有动作知道——这份分工让「结束过程的前置状态」这个事实**只写得出一遍**。</p>
 *
 * <p>⚠ <b>装配期 fail fast</b>（一律 {@link IllegalStateException}，即服务起不来）：主链为空 / 元素为空值 /
 * 出现 {@link #ENDING_TARGETS} / 同一个状态出现两次 / 首项不是 {@link OrderStatus#PENDING_PAYMENT} /
 * 主链加两个结束过程的落点没覆盖 {@link OrderStatus} 全部常量 /
 * {@link OrderStatus#ENDED 「已结束」的声明}与「主链末项 + 两个落点」不一致 —— 全部在装配期炸。
 * 漏一个状态的后果是「那个状态永远到不了」，它不报错、只会让某笔订单卡死，属于最难查的一类问题，
 * 所以必须让服务起不来。</p>
 *
 * <p>⚠ 两类错误刻意用两种异常：<b>配置 / 装配错</b>是程序员错误 → {@code IllegalStateException}；
 * <b>用户可见的非法迁移</b>是业务错误 → {@code ServiceException(400)}，提示用两侧的 mallLabel
 * 拼成可直接展示的中文（裁定 D12）。</p>
 */
public final class OrderStatusFlow {

    /**
     * **不进主链**的两个状态：它们由「结束过程」到达（未支付 → 已取消、已支付 → 已退款）。
     *
     * <p>⚠ 这里只声明「它们不参与主链」，**不声明它们从哪来**——那是动作的定义
     * （{@link OrderModel#markCancelled} / {@link OrderModel#markRefunded}）。旧版把这一对
     * 当前驱集合配在图上，撤掉图之后这一对也不该换个地方继续存在：它的唯一落点是动作。</p>
     */
    private static final List<OrderStatus> ENDING_TARGETS =
            List.of(OrderStatus.CANCELLED, OrderStatus.REFUNDED);

    /** 主链（不可变）：首项是入口，相邻两项之间的边就是「推进一步」 */
    private final List<OrderStatus> chain;

    /**
     * 由配置的主链构建状态机
     *
     * @param statusFlow 主链（**按先后顺序**；首项必须是 {@link OrderStatus#PENDING_PAYMENT}，
     *                   且**不含** {@link OrderStatus#CANCELLED} / {@link OrderStatus#REFUNDED}）
     * @throws IllegalStateException 上一条 javadoc 里列的七种配置错之一
     */
    public OrderStatusFlow(List<OrderStatus> statusFlow) {
        if (statusFlow == null || statusFlow.isEmpty()) {
            throw new IllegalStateException("订单状态主链配置不能为空（panoramic.trade.order.status-flow 里"
                    + "按先后顺序列出主链上的状态，首项就是那个初始状态）");
        }
        List<OrderStatus> declared = new ArrayList<>(statusFlow.size());
        Set<OrderStatus> seen = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus status : statusFlow) {
            if (status == null) {
                throw new IllegalStateException("订单状态主链配置里出现空值（status-flow 只允许写状态常量名）");
            }
            if (ENDING_TARGETS.contains(status)) {
                throw new IllegalStateException("订单状态主链配置里出现了 " + status.name()
                        + "：它由**结束过程**到达，从哪来写在动作方法里（取消见 OrderModel#markCancelled、"
                        + "仅退款见 OrderModel#markRefunded），不配在主链上");
            }
            if (!seen.add(status)) {
                throw new IllegalStateException("订单状态主链配置里 " + status.name()
                        + " 出现了不止一次（数组顺序即先后，同一个状态出现两次就没有先后可言了）");
            }
            declared.add(status);
        }

        // ① 首项只能是「待支付」：开单即待支付这件事写在 OrderModel#open 里（不来自配置），
        //    配置说了不算 —— 两边不一致时，每一笔单读回来都会在轨迹对账那一步炸（起服务时就该炸）
        if (declared.get(0) != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("订单状态主链配置的首项是 " + declared.get(0).name()
                    + "：开单即「待支付」写在 OrderModel#open 里（不来自配置），故主链首项必须是 "
                    + OrderStatus.PENDING_PAYMENT.name());
        }

        // ② 覆盖全部常量：漏一个状态的后果是「它永远到不了」，且要到某笔单卡死时才发现
        Set<OrderStatus> missing = EnumSet.allOf(OrderStatus.class);
        missing.removeAll(declared);
        missing.removeAll(ENDING_TARGETS);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("订单状态流转配置缺少状态：" + names(missing)
                    + "（缺一个就有一个状态永远到不了，故装配期直接失败）");
        }

        // ③ 「已结束」的声明必须与整张图一致：OrderStatus#ENDED 是第二级幂等去重的判据
        //    （已结束的单不参与复用），而「谁还能往前走」由主链 + 两个结束过程给定。两边不一致的后果是：
        //    一笔已经走到头的单被当成在途单**复用来顶掉新单**——静默错，顾客看到的是「下单成功」。
        //    判据 = 主链末项（正道走到头）+ 两个结束过程的落点。
        Set<OrderStatus> sinks = EnumSet.noneOf(OrderStatus.class);
        sinks.add(declared.get(declared.size() - 1));
        sinks.addAll(ENDING_TARGETS);
        Set<OrderStatus> declaredEnded = EnumSet.allOf(OrderStatus.class).stream()
                .filter(OrderStatus::isEnded)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OrderStatus.class)));
        if (!sinks.equals(declaredEnded)) {
            throw new IllegalStateException("订单状态流转配置与 OrderStatus 里「已结束」的声明不一致："
                    + "「主链末项 + 两个结束过程的落点」是 " + names(sinks) + "，而 OrderStatus 声明的是 "
                    + names(declaredEnded)
                    + "（已结束的状态不参与第二级幂等去重，两边不一致会让一笔已走到头的单被复用来顶掉新单）");
        }

        this.chain = List.copyOf(declared);
    }

    /**
     * @return 入口状态（轨迹的首项、下单即处于的状态）= 主链首项
     */
    public OrderStatus initialState() {
        return chain.get(0);
    }

    /**
     * @return 主链（**不可变副本**，按先后顺序；可直接持有）
     */
    public List<OrderStatus> chain() {
        return chain;
    }

    /**
     * @param status 状态
     * @return 主链上该状态的**下一个**；末态、或不在主链上的状态（已取消 / 已退款）为 {@code null}
     */
    public OrderStatus nextOf(OrderStatus status) {
        Objects.requireNonNull(status, "订单状态不能为空");
        int index = chain.indexOf(status);
        return (index < 0 || index == chain.size() - 1) ? null : chain.get(index + 1);
    }

    /**
     * 沿主链推进一步（三个主链动作走这里：付款 / 发货 / 收货）
     *
     * <p>判据只有一条：<b>目标必须是当前状态在主链上的下一个</b>——配置是主链先后唯一的来源。
     * 跳级、回退、原地重复都会落在这里。</p>
     *
     * <p>⚠ <b>未 seal 的订单在这里就被拦下</b>：一笔还没补全的订单不能是「待支付」（裁定见 {@link OrderModel}）。
     * 本类的检查与 {@link OrderModel#applyStatus} 里的检查是**刻意重复**的——入口有两个，
     * 异常类型不能因走哪个入口而不同（装配顺序错就该是 {@code IllegalStateException}，
     * 不该因为先撞上迁移校验而变成 400 业务错）。</p>
     *
     * @param order  目标订单
     * @param target 目标状态（必须是当前状态在主链上的下一个）
     * @throws IllegalStateException 订单尚未 seal
     * @throws ServiceException      非法迁移（不是主链的下一步 / 原地重复变更），HTTP 400
     */
    public void advance(OrderModel order, OrderStatus target) {
        Objects.requireNonNull(order, "订单不能为空");
        Objects.requireNonNull(target, "目标订单状态不能为空");
        assertSealed(order);
        OrderStatus current = order.getStatus();
        if (nextOf(current) != target) {
            throw cannotMove(current, target);
        }
        order.applyStatus(target);
    }

    /**
     * 走一个**结束过程**（两个动作走这里：取消 / 仅退款）
     *
     * <p>「这笔单此刻必须停在 {@code from}」由**调用方给出**——那个 from 就是「取消只能从未支付来」
     * 这句话本身，它写在 {@link OrderModel#markCancelled} / {@link OrderModel#markRefunded} 里，
     * 不来自配置、也不来自本类。本类只做两件事：把动作声明的 from 与这笔单当前状态对上，
     * 以及**挡住「拿结束过程走主链」**（见下）。</p>
     *
     * <p>⚠ 目标必须是 {@link #ENDING_TARGETS} 里那两个之一：主链上的状态只能由 {@link #advance} 推进，
     * 否则「主链顺序来自配置」这句话就被绕过去了（把付款写成 {@code endWith(this, PENDING_PAYMENT, PAID)}
     * 就不必再管配置怎么排）。这是**编程错误**（{@code IllegalStateException}），不是用户输入问题。</p>
     *
     * @param order  目标订单
     * @param from   这笔单此刻必须处于的状态（**动作的定义**：取消 = 待支付、仅退款 = 已支付）
     * @param target 结束过程的落点（已取消 / 已退款）
     * @throws IllegalStateException 订单尚未 seal，或 target 在主链上（该走 {@link #advance}）
     * @throws ServiceException      这笔单此刻不停在 {@code from}（HTTP 400），或原地重复变更
     */
    public void endWith(OrderModel order, OrderStatus from, OrderStatus target) {
        Objects.requireNonNull(order, "订单不能为空");
        Objects.requireNonNull(from, "结束过程的来源状态不能为空");
        Objects.requireNonNull(target, "目标订单状态不能为空");
        assertSealed(order);
        if (chain.contains(target)) {
            throw new IllegalStateException("订单状态 " + target.name() + " 在主链上，不能用结束过程到达"
                    + "（结束过程只用于 " + names(ENDING_TARGETS) + "；主链的下一步走 OrderStatusFlow#advance）");
        }
        OrderStatus current = order.getStatus();
        if (current != from) {
            throw cannotMove(current, target);
        }
        order.applyStatus(target);
    }

    /**
     * 校验一条状态轨迹是**一条合法路径**（读侧与写侧共用同一份判据，不各写一遍）
     *
     * <p>轨迹是「只前不退」在数据上的证据，合法形状只有两种：</p>
     * <ul>
     *   <li><b>主链的一段前缀</b>：{@code [待支付]}、{@code [待支付, 已支付]}、… ——
     *       第 i 项必须**正好是主链第 i 项**（跳级 / 回退 / 从中间开始都对不上）；</li>
     *   <li><b>主链的一段前缀 + 一个结束过程收尾</b>：{@code [待支付, 已取消]}、
     *       {@code [待支付, 已支付, 已退款]} —— 不在主链上的状态只能是**最后一项**，
     *       且只能出现一次（一笔单到那里就完了，后面不该还有状态）。</li>
     * </ul>
     *
     * <p>⚠ <b>这里不校验「那个结束过程的前置状态对不对」</b>（如「已支付 → 已取消」是不是写错了）：
     * 那是**写侧**的职责（动作方法自己判，{@link #endWith}），本类没有那份「从哪来」的数据——
     * 与其在这里再造一份，不如让判据只有一处。被写坏的轨迹由写侧的测试与
     * {@code JdbcOrderRepository#insertStatusTrail} 的写入前校验一起挡住。</p>
     *
     * <p>⚠ 两个调用方：{@link OrderModel#rehydrate}（读出来的轨迹）与
     * {@code JdbcOrderRepository#insertStatusTrail}（要写下去的轨迹）。{@code seq} 只是**轨迹序号**
     * （0、1、2…），不等于「状态在主链上的下标」——故两处都不能拿下标做校验，只有本方法这一份判据。</p>
     *
     * @param orderNo 订单号（只用于报错信息点名是哪一笔单）
     * @param trail   轨迹（从入口状态开始的完整路径）
     * @throws IllegalStateException 轨迹为空、不是从入口状态开始、或不是上面两种形状之一
     */
    public void assertLegalTrail(String orderNo, List<OrderStatus> trail) {
        Objects.requireNonNull(orderNo, "订单号不能为空");
        if (trail == null || trail.isEmpty()) {
            throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹为空（轨迹的语义是「这笔单走过哪些状态」，"
                    + "空轨迹不能重建）");
        }
        if (trail.get(0) != chain.get(0)) {
            throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹不是从初始状态开始的（首项="
                    + nameOf(trail.get(0)) + "，主链首项=" + chain.get(0).name() + "）");
        }
        for (int i = 1; i < trail.size(); i++) {
            OrderStatus to = trail.get(i);
            if (to == null) {
                throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹第 " + (i + 1) + " 项是空值");
            }
            if (!chain.contains(to)) {
                // 不在主链上的状态只有那两个结束过程的落点（装配期已断言主链 + 落点覆盖全部常量），
                // 它们只能收尾 —— 后面还有状态就说明「结束之后又走了一步」
                if (i != trail.size() - 1) {
                    throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹里 " + to.name()
                            + " 不是最后一项（" + names(ENDING_TARGETS) + " 是结束过程的落点，"
                            + "一笔单到那里就完了，后面不该还有状态）");
                }
                continue;
            }
            // 主链上的状态：轨迹必须与主链逐项对齐（第 i 项 == 主链第 i 项）。
            // 这一条同时挡住跳级（第 2 项就是「已发货」）、回退（第 2 项又是「待支付」）与
            // 「主链走完了还多一项」（i 越过主链末尾）
            if (i >= chain.size() || chain.get(i) != to) {
                throw new IllegalStateException("订单 " + orderNo + " 的状态轨迹不是一条合法路径：第 " + (i + 1)
                        + " 项是 " + nameOf(to) + "，而主链上第 " + (i + 1) + " 项应当是 "
                        + (i < chain.size() ? chain.get(i).name() : "（没有更多状态）"));
            }
        }
    }

    /**
     * 「这次状态改动不合法」的那句 400（**提示语的唯一来源**）
     *
     * <p>调用它的地方有三处：{@link #advance} 与 {@link #endWith} 判下来不合法时，
     * 以及 {@code JdbcOrderRepository#update} 条件更新「0 行」的那条路径——那里拿库里的当前状态
     * 重跑一遍这句话，于是「顺序调用被拒」与「并发被抢先」得到**同一句** 400。
     * ⚠ 那句「重复变更」被 [mall-bff.md] 的契约注释引用，改它要同批改契约。</p>
     *
     * @param from 动作所在的状态（库里/单子上的实际状态）
     * @param to   目标状态
     * @return 该抛的异常（**一定是不合法的组合**：库里那个状态既不等于动作声明的来时状态，
     *         也就不可能是它的来源；主链上「下一个」更是唯一的）
     */
    public static ServiceException cannotMove(OrderStatus from, OrderStatus to) {
        Objects.requireNonNull(from, "订单当前状态不能为空");
        Objects.requireNonNull(to, "目标订单状态不能为空");
        if (from == to) {
            return new ServiceException(400, "订单状态不能从「" + from.getMallLabel() + "」重复变更到「"
                    + to.getMallLabel() + "」");
        }
        return new ServiceException(400, "订单状态不能从「" + from.getMallLabel() + "」变更为「" + to.getMallLabel() + "」");
    }

    /** 状态迁移入口的统一闸门（未 seal 的模型不能被当成订单使用） */
    private static void assertSealed(OrderModel order) {
        if (!order.isSealed()) {
            throw new IllegalStateException("订单 " + order.getOrderNo() + " 尚未封存（seal），不能迁移状态");
        }
    }

    /** 把状态列表写成报错信息里可读的一串（枚举名，不是两侧文案——这里给的是运维看的口径） */
    private static String names(Collection<OrderStatus> statuses) {
        return statuses.stream().map(OrderStatus::name).collect(Collectors.joining("、"));
    }

    /** 轨迹里出现 {@code null} 时把它渲染成 {@code "null"}，不抛异常（这条路径是「报数据被写坏」） */
    private static String nameOf(OrderStatus status) {
        return (status == null) ? "null" : status.name();
    }
}
