package com.panoramic.storebff;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 店铺端 BFF 启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper（store_user 账号栈归本模块）。
 * 店铺端 BFF 消费 store 域「店主能力」：扫描 common 中同源的内部 Feign 客户端
 * （com.panoramic.common.store）。与 admin（平台端）互不调用、互不互通（D2/D6）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.storebff.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.panoramic.common.store")
public class StoreBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(StoreBffApplication.class, args);
    }
}
