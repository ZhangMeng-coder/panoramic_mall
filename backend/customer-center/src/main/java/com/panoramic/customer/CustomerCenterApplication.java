package com.panoramic.customer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 顾客域（下沉纯域）启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper。
 * customer-center 域仅持 customer_profile / customer_address，不启用 Feign 客户端扫描（纯被调方）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.customer.mapper")
@EnableDiscoveryClient
public class CustomerCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerCenterApplication.class, args);
    }
}
