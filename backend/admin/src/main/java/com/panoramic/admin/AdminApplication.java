package com.panoramic.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.admin.mapper")
@EnableDiscoveryClient
// admin 端 BFF 消费 goods-center（标准商品平台）、store（店铺域）等下沉域：
// 扫描 common 中同源的内部 Feign 客户端
@EnableFeignClients(basePackages = {"com.panoramic.common.goods", "com.panoramic.common.store"})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
