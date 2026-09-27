package com.panoramic.trade.order.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 销量推送的**运维护栏**（{@code panoramic.trade.order.sales-push.*}）——三个数都不是业务口径。
 *
 * <p>业务口径只有一条：「销量记订单完成的件数」（在 {@code OrderSalesPushService} 里），
 * 这里的三项分别管「后台推得多快」「最多占几个线程」「一轮最多捞几笔」。把它们与口径分开写，
 * 是因为改它们的动机完全不同：口径变了要过需求门，这几项变了只是调运维参数。</p>
 *
 * <p>⚠ 与 {@code OrderProperties} 分开一个类而不是塞进去：那个类是**订单域自己的**口径
 * （步骤链、状态主链、支付时限），销量推送是**跨域协作**的运维参数，混在一起会让
 * 「本域顺序流程会变的部分」与「一条跨域边的运行参数」看起来是一回事。</p>
 *
 * <p>⚠ <b>本类只承载配置，不做校验</b>——校验点在两个真正的消费者：
 * {@code SalesPushConfig} 断言线程池参数（除零 / 负队列会让线程池静默失效），
 * {@code OrderSalesPushTask} 断言单批上限（0 或负数会让每一轮都捞不到单）。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "panoramic.trade.order.sales-push")
public class SalesPushProperties {

    /**
     * 推送线程池的并发度（同时最多几笔在推）
     *
     * <p>⚠ 它不需要大：推送是一笔一次 Feign 调用，且**只在收货时**发生（不是查询路径）。
     * 给 2 是为了「某一个下游卡住时不至于把后续推送全堵在同一个线程上」，不是为了提高吞吐。</p>
     */
    private int poolSize = 2;

    /**
     * 推送线程池的排队上限（必须有界）
     *
     * <p>⚠ 队列无界时，池子来不及处理的任务会一直堆在内存里，最终以 OOM 收场——
     * 而且是在「推送慢」这个已经不正常的状态下悄悄恶化的。有界 + CallerRuns 会把压力
     * 显式地回落到提交线程（见 {@code SalesPushConfig}）。</p>
     */
    private int queueCapacity = 500;

    /**
     * 补推任务的**单批上限**（一轮扫描最多推几笔）
     *
     * <p>⚠ 积压是预期的：{@code sales_pushed} 列上线时，**全部历史已完成单都是未推送**
     * （这正是销量回填的机制，见 {@code OrderRepository#findReceivedUnpushed}），
     * 加上运行期推送失败的单。不限量就会让一轮扫描的耗时与内存随积压量线性增长。
     * 上限内的先推、下一轮再推剩下的——任务幂等（推成功即置标记），故分批不会漏。</p>
     */
    private int batchSize = 200;
}
