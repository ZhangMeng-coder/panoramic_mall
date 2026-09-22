package com.panoramic.trade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 交易域（下沉纯域）启动类
 * <p>scanBasePackages=com.panoramic 加载 common 的全局异常处理、分页插件、
 * 字段自动填充与安全链；MapperScan 仅扫本服务 mapper。
 * trade-center 持 trade_cart_item（购物车）与订单 5 张表（trade_order 等）。</p>
 * <p><b>⚠ 本域是全仓唯一开了 Feign 客户端扫描的域</b>：下单要查 store 域的商品快照 /
 * 扣减库存（订单生成流水线的 goods-check 与 stock-check 两个步骤），故只扫
 * {@code com.panoramic.contract.store}（store 域的 Feign 客户端包），不扫别的域——
 * 扫描范围即「本域承认的跨域依赖边」，多扫一个包等于静默多一条依赖。
 * 这是「各域只依赖自己的 {@code <域>-interface}」的**登记例外**，
 * 见 docs/contracts/cross-cutting.md 第 24 条；据此本服务也加载 {@code feign-circuitbreaker.yml}
 * （域间调用同样要熔断：store 不可达时下单必须整体失败，不得被拖死）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.panoramic")
@MapperScan({"com.panoramic.trade.mapper", "com.panoramic.trade.order.infrastructure.mapper"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.panoramic.contract.store")
public class TradeCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeCenterApplication.class, args);
    }
}
