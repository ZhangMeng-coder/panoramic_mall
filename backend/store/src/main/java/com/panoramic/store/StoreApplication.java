package com.panoramic.store;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 店铺业务域（下沉纯域）启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper。
 * store 域仅持 store_shop，不启用 Feign 客户端扫描（纯被调方）。</p>
 * <p><b>{@code @EnableAsync} / {@code @EnableScheduling}（2026-09-26 新增，本域首次引入异步与调度）</b>：
 * 排序分 {@code rank_score} 的重算要「不阻塞写路径、且最终一致」，落法就是「提交后异步算一次
 * ＋ 定时扫脏行兜底」。两个开关是「本应用要不要这套基础设施」，故挂启动类；
 * 线程池怎么配在 {@code config/RankRecalcConfig}，业务侧见 {@code rank/} 包。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.store.mapper")
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
public class StoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreApplication.class, args);
    }
}
