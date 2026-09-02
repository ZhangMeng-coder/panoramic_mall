# gateway — API 网关

全景商城统一入口网关，基于 **Spring Cloud Gateway（WebFlux 响应式）**，端口 **8080**。

## 核心功能

- **服务路由**：将外部请求按路径转发至注册在 Nacos 上的后端服务（`lb://` 负载均衡）
- **前缀剥离**：`StripPrefix=1`，网关层前缀与业务服务内部路径解耦
- **服务探活**：`/discovery/services` 返回当前 Nacos 已注册服务列表，便于快速验证链路

## 路由规则

| 网关路径 | 转发目标 | 说明 |
|---|---|---|
| `/goods/**` | `lb://goods-center`（StripPrefix=1） | 商品中台接口。业务服务内路径不带 `/goods` 前缀，例如 `GET /goods/categories/tree` → 服务内 `/categories/tree` |

新增业务服务时，在 `src/main/resources/application.yml` 的 `spring.cloud.gateway.server.webflux.routes` 下追加路由即可。

## 快速验证

```bash
# 查看注册中心服务列表（应看到 gateway、goods-center）
curl http://localhost:8080/discovery/services

# 经网关访问商品中心（Nacos + goods-center 须已启动）
curl http://localhost:8080/goods/categories/tree
```

## 依赖

- Nacos 服务发现（地址/账号由父 POM properties 经 Maven 过滤注入）
- 负载均衡：Spring Cloud LoadBalancer（非 Ribbon）
