package com.panoramic.mallbff;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 商城前台 BFF 启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper（mall_user 账号栈归本模块）。
 * 已接 goods-center（分类树）与 store（商品分页/筛选聚合）——两个域的 Feign 契约与
 * DTO/VO 同源维护在 common 的 {@code com.panoramic.common.goods} /
 * {@code com.panoramic.common.store} 包，故一并作为扫描包（与 admin / store-bff 一致）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.mallbff.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.panoramic.common.store", "com.panoramic.common.goods"})
public class MallBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallBffApplication.class, args);
    }
}
