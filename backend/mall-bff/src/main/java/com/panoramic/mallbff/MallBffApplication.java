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
 * 已接 goods-center（分类树）、store（商品分页/筛选聚合/详情）与 customer-center（顾客资料）——
 * 三个域的 Feign 契约与 DTO/VO 同源维护在各自的接口模块里（{@code com.panoramic.contract.goods} 在
 * goods-center-interface、{@code com.panoramic.contract.store} 在 store-interface、
 * {@code com.panoramic.contract.customer} 在 customer-center-interface），
 * 故三个包一并作为扫描包（与 admin / store-bff 一致）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.mallbff.mapper")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.panoramic.contract.store", "com.panoramic.contract.goods",
        "com.panoramic.contract.customer"})
public class MallBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallBffApplication.class, args);
    }
}
