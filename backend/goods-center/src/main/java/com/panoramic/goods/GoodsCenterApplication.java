package com.panoramic.goods;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.goods.mapper")
@EnableDiscoveryClient
public class GoodsCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsCenterApplication.class, args);
    }
}
