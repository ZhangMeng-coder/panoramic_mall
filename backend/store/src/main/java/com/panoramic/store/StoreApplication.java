package com.panoramic.store;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 店铺业务域（下沉纯域）启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper。
 * store 域仅持 store_shop，不启用 Feign 客户端扫描（纯被调方）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.store.mapper")
@EnableDiscoveryClient
public class StoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreApplication.class, args);
    }
}
