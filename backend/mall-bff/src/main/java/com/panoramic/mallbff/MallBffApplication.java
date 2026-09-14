package com.panoramic.mallbff;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商城前台 BFF 启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper（mall_user 账号栈归本模块）。
 * 一期只做 C 端账号（注册/登录/登出/me），**不调任何业务域**，故不加
 * {@code @EnableFeignClients}——{@code com.panoramic.common.mall} 包尚不存在。
 * 二期接入首页数据聚合（goods-center 的商品/分类）时再补该注解与扫描包。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan("com.panoramic.mallbff.mapper")
@EnableDiscoveryClient
public class MallBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallBffApplication.class, args);
    }
}
