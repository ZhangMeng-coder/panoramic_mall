package com.panoramic.store.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 排序分重算的装配层：定义 {@code @Async} 用的线程池，并绑定 {@link StoreRankProperties}。
 *
 * <p>⚠ <b>{@code @EnableAsync} / {@code @EnableScheduling} 不在这里，在 {@code StoreApplication}</b>
 * （与 trade-center 把 {@code @EnableScheduling} 挂启动类同款）：它们是「整个应用要不要这套基础设施」的
 * 开关，属启动类；本类只管「这套基础设施怎么配」。本域此前零异步 / 零调度，本次是首次引入。</p>
 *
 * <h3>为什么必须自带线程池，不能用 Spring 的默认执行器</h3>
 * <p>{@code @Async} 不指定执行器时会落到框架默认的 {@code applicationTaskExecutor}——那是给
 * 「谁都能用」的通用池（无界队列、与其它异步任务争同一批线程）。重算任务有明确的量纲
 * （变更频率 × 商品数），给它自己的池才能独立限流：池满时是它自己慢，而不是把别的异步任务一起拖住。</p>
 *
 * <h3>拒绝策略为什么是 CallerRuns 而不是 Abort</h3>
 * <p>重算任务的提交点在<b>业务事务提交之后</b>（{@code AFTER_COMMIT}，见 {@code RankRecalcListener}）。
 * 此刻业务写已经落库、请求正在返回路上——若用 {@code AbortPolicy}，池满时会抛
 * {@code RejectedExecutionException}，它<b>回不到</b>调用方（{@code @Async} 侧已经交出控制权），
 * 只会变成一条「一个已成功的请求最后炸了」的错误日志；更坏的是同一个提交点若被别处复用，
 * 会把已提交的写变成 500。</p>
 * <p>{@code CallerRunsPolicy} 则：① <b>永不丢任务</b>（最坏退化成提交线程同步算一次，算分只是一读一写），
 * ② <b>永不抛</b>（它不抛异常，池满时把压力显式地回落到提交线程——写路径变慢是可见的信号，
 * 比静默丢任务好）。⚠ 这条策略只对<b>写路径</b>成立：重算不发生在查询路径上，
 * 故「偶发地让一次改价同步等一下算分」是可接受的代价。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(StoreRankProperties.class)
public class RankRecalcConfig {

    /**
     * 排序分重算线程池（{@code @Async("rankRecalcExecutor")} 按 bean 名取它）
     *
     * <p>⚠ 队列必须有界（见 {@link StoreRankProperties#getQueueCapacity()}），
     * 且线程池要给名字——线上排查「谁在跑算分」时，线程名 {@code rank-recalc-*} 是唯一的线索。</p>
     *
     * @param properties 运行参数（并发度 / 排队上限）
     * @return 重算专用线程池
     * @throws IllegalStateException 并发度或排队上限非正（配置错是程序员错误，装配期就让它起不来）
     */
    @Bean("rankRecalcExecutor")
    public ThreadPoolTaskExecutor rankRecalcExecutor(StoreRankProperties properties) {
        int poolSize = properties.getPoolSize();
        int queueCapacity = properties.getQueueCapacity();
        // 两个上限必须为正：并发度 ≤0 会让线程池一个线程都不建（@Async 提交即被拒），
        // 队列 ≤0 则退化成「线程池满了就直接跑在调用者线程上」——两种情况都不报错，只表现为
        // 「重算好像没在异步跑」。配置错是程序员错误，装配期就让它起不来（同 OrderTimeoutCloseTask 的断言）
        if (poolSize <= 0 || queueCapacity <= 0) {
            throw new IllegalStateException("排序分重算线程池参数必须为正数（panoramic.store.rank.pool-size="
                    + poolSize + "，queue-capacity=" + queueCapacity + "）");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("rank-recalc-");
        // 见类注释：永不丢任务、永不抛
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关停时不等待在途重算（它幂等，脏标记没清就下一轮兜底扫描会重捞），但等一小会儿让正在写的那条收尾
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("排序分重算线程池已装配：poolSize={}，queueCapacity={}", poolSize, queueCapacity);
        return executor;
    }
}
