package com.panoramic.trade.order.application.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 销量推送的装配层：定义 {@code @Async} 用的线程池，并绑定 {@link SalesPushProperties}。
 *
 * <p>⚠ <b>{@code @EnableAsync} 不在这里，在 {@code TradeCenterApplication}</b>
 * （与 {@code @EnableScheduling} 挂启动类同款）：那是「整个应用要不要这套基础设施」的开关，
 * 属启动类；本类只管「这套基础设施怎么配」。</p>
 *
 * <h3>为什么必须自带线程池，不能用 Spring 的默认执行器</h3>
 * <p>{@code @Async} 不指定执行器时会落到框架默认的 {@code applicationTaskExecutor}——那是给
 * 「谁都能用」的通用池。推送有明确的量纲（订单完成频率），且它的下游（store 域）可能慢或挂，
 * 给它自己的池才能独立限流：推送堵住时是它自己慢，而不是把本域别的异步任务一起拖住。</p>
 *
 * <h3>拒绝策略为什么是 CallerRuns 而不是 Abort</h3>
 * <p>推送的提交点在<b>收货事务提交之后</b>（{@code AFTER_COMMIT}，见 {@code OrderSalesPushListener}）。
 * 此刻收货已经落库、请求正在返回路上——若用 {@code AbortPolicy}，池满时会抛
 * {@code RejectedExecutionException}，而它<b>回不到</b>调用方（{@code @Async} 侧已经交出控制权），
 * 只会变成一条「一个已成功的收货最后炸了」的错误日志。</p>
 * <p>{@code CallerRunsPolicy} 则：① <b>永不丢任务</b>（最坏退化成提交线程同步推一次，
 * 而这正是「兜底扫描也能补」的那件事，丢了才真的要靠扫描），② <b>永不抛</b>（池满时把压力显式
 * 回落到提交线程——收货变慢是可见的信号，比静默丢一次推送好）。⚠ 这条策略的代价是
 * 「池满时收货会同步等一次 Feign」——但那只发生在推送长期积压时，且比丢推送可接受。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SalesPushProperties.class)
public class SalesPushConfig {

    /**
     * 销量推送线程池（{@code @Async("salesPushExecutor")} 按 bean 名取它）
     *
     * <p>⚠ 队列必须有界（见 {@link SalesPushProperties#getQueueCapacity()}），
     * 且线程池要给名字——线上排查「谁在推销量」时，线程名 {@code sales-push-*} 是唯一的线索。</p>
     *
     * @param properties 运行参数（并发度 / 排队上限）
     * @return 推送专用线程池
     * @throws IllegalStateException 并发度或排队上限非正（配置错是程序员错误，装配期就让它起不来）
     */
    @Bean("salesPushExecutor")
    public ThreadPoolTaskExecutor salesPushExecutor(SalesPushProperties properties) {
        int poolSize = properties.getPoolSize();
        int queueCapacity = properties.getQueueCapacity();
        // 两个上限必须为正：并发度 ≤0 会让线程池一个线程都不建（@Async 提交即被拒），
        // 队列 ≤0 则退化成「线程池满了就直接跑在调用者线程上」——两种情况都不报错，只表现为
        // 「推送好像没在异步跑」。配置错是程序员错误，装配期就让它起不来（同 OrderTimeoutCloseTask 的断言）
        if (poolSize <= 0 || queueCapacity <= 0) {
            throw new IllegalStateException("销量推送线程池参数必须为正数（panoramic.trade.order.sales-push.pool-size="
                    + poolSize + "，queue-capacity=" + queueCapacity + "）");
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("sales-push-");
        // 见类注释：永不丢任务、永不抛
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关停时等一小会儿：推送是「至少一次」的，等它收尾能少一轮补推；等不到也不丢，
        // 未推送标记还在，重启后兜底扫描接手
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        log.info("销量推送线程池已装配：poolSize={}，queueCapacity={}", poolSize, queueCapacity);
        return executor;
    }
}
