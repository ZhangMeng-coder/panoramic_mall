package com.panoramic.gateway.controller;

import java.util.List;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 服务发现查询接口：返回 gateway 当前能从注册中心（Nacos）发现的所有服务及其实例地址。
 * 可用于验证注册中心连通性、服务注册情况，以及 lb:// 路由能否解析到目标实例。
 */
@RestController
@RequestMapping("/discovery")
public class DiscoveryController {

    private final DiscoveryClient discoveryClient;

    public DiscoveryController(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * GET /discovery/services
     * 返回示例：
     * [{"service":"gateway","instances":["192.168.3.7:8080"]},
     *  {"service":"goods-center","instances":["192.168.3.7:8081"]}]
     */
    @GetMapping("/services")
    public Mono<List<ServiceInfo>> services() {
        // Nacos 的 DiscoveryClient 是阻塞实现，放到 boundedElastic 线程池执行，避免阻塞 Netty 事件循环
        return Mono.fromCallable(() -> discoveryClient.getServices().stream()
                        .map(this::toServiceInfo)
                        .toList())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private ServiceInfo toServiceInfo(String serviceId) {
        List<String> instances = discoveryClient.getInstances(serviceId).stream()
                .map(instance -> instance.getHost() + ":" + instance.getPort())
                .sorted()
                .toList();
        return new ServiceInfo(serviceId, instances);
    }

    /** 单个服务在注册中心中的发现信息 */
    public record ServiceInfo(String service, List<String> instances) {
    }
}
