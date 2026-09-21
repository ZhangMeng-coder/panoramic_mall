package com.panoramic.trade.order.infrastructure;

import com.panoramic.trade.order.domain.OrderNoGenerator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

/**
 * 订单号生成器的默认实现（裁定 D5）：<b>{@code yyyyMMddHHmmss} + 4 位序列</b>，共 <b>18 位</b>、定长。
 *
 * <p>格式 {@code yyyyMMddHHmmss}（14 位，本地时区）+ 4 位十进制序列，例如
 * {@code 202609211530000042}。前 14 位让单号**业务可读**（客服报单号时能直接看出下单时刻），
 * 后 4 位让同一秒内的多笔订单彼此可区分（极端情况下才需要）。</p>
 *
 * <p>⚠ <b>本类不做去重</b>：「生成 → 查重 → 重试」在编排层完成（编排层才知道重试上限这项业务口径，
 * 也只有它看得见仓库与同一批次的其它单号）。本类若是自己维护一张去重表，就会出现
 * **第二份「什么算重复」的口径**，而且它在多实例部署下根本不成立。</p>
 *
 * <p>⚠ <b>时钟与序列源都可注入</b>：单测注入固定 {@code Clock} 与固定序列后单号完全可预测，
 * 「同秒内可区分」「撞车后换一个」这类断言才有办法写；生产用默认的无参构造即可。
 * 序列用 4 位随机数而不是进程内自增计数器：单号要在多实例之间也尽量少撞，
 * 而本进程内自增在同一个 4 位空间里反而更容易与别的实例撞。</p>
 */
public class DefaultOrderNoGenerator implements OrderNoGenerator {

    /** 前 14 位：下单时刻（本地时区，业务可读） */
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 序列位宽：4 位十进制，故取值范围 {@code 0..9999} */
    private static final int SEQUENCE_BOUND = 10000;

    /** 序列格式：定宽 4 位（不足补零，保证单号总长恒为 18） */
    private static final String SEQUENCE_FORMAT = "%04d";

    private final Clock clock;
    private final IntSupplier sequence;

    /**
     * 生产用构造器：序列取 4 位随机数
     *
     * @param clock 时钟（生产为系统时钟；注入是为了让单测的「同秒」可复现）
     */
    public DefaultOrderNoGenerator(Clock clock) {
        this(clock, () -> ThreadLocalRandom.current().nextInt(SEQUENCE_BOUND));
    }

    /**
     * 测试用构造器：序列可注入
     *
     * @param clock    时钟
     * @param sequence 序列源（值会被规整到 {@code 0..9999}，故注入越界值也不会破坏定长）
     */
    public DefaultOrderNoGenerator(Clock clock, IntSupplier sequence) {
        this.clock = clock;
        this.sequence = sequence;
    }

    /**
     * 生成一个新单号
     *
     * @return 18 位单号：{@code yyyyMMddHHmmss} + 4 位序列
     */
    @Override
    public String next() {
        // floorMod 而非 %：注入的序列源若给了负数/超界值，取模仍落在 0..9999，
        // 单号不会因为一个越界值就变长（长度是契约的一部分，前端按它做定宽展示）
        int seq = Math.floorMod(sequence.getAsInt(), SEQUENCE_BOUND);
        return TIMESTAMP_FORMAT.format(LocalDateTime.now(clock)) + String.format(SEQUENCE_FORMAT, seq);
    }
}
