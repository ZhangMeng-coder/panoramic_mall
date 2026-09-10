# gateway — API 网关

全景商城统一入口网关，基于 **Spring Cloud Gateway（WebFlux 响应式）**，端口 **8080**。

## 核心功能

- **服务路由**：将外部请求按路径转发至注册在 Nacos 上的**端 BFF**（`lb://` 负载均衡）
- **公网入口白名单**：`BffRouteGuardFilter` 强制校验 `panoramic.gateway.bff-services`，**名单外服务（含业务域 goods-center / store）经网关一律 403**——域服务只由 BFF 经注册中心内部 Feign 调用
- **鉴权透传**：`AuthGlobalFilter` 对非白名单路径验 JWT 签名/有效期 → 解析 `type` claim → 校验 Redis `panoramic:login:{type}:{userId}` 登录态仍有效 → 注入 `X-User-Id` / `X-User-Type` 给下游
- **前缀剥离**：`StripPrefix=1`，网关层前缀与业务服务内部路径解耦
- **服务探活**：`/discovery/services` 返回当前 Nacos 已注册服务列表，便于快速验证链路

## 路由规则

| 网关路径 | 转发目标 | 说明 |
|---|---|---|
| `/admin/**` | `lb://admin`（StripPrefix=1） | 平台端 BFF：账号/RBAC + 商品模板编排 + 店铺管理审核 |
| `/store/**` | `lb://store-bff`（StripPrefix=1） | 店铺端 BFF：店主注册/登录 + 店铺资料编排 |

新增端 BFF 时：在 `application.yml` 的 `spring.cloud.gateway.server.webflux.routes` 追加路由，并把它加入 `panoramic.gateway.bff-services` 白名单（两处缺一不可）。

> 业务域服务**不开公网路由**（历史 `/goods/** → goods-center` 已移除）。

## 鉴权配置

`jwt-secret` / `jwt-expire-seconds` / `redis-prefix` / `header-name` / `whitelist-paths` 均来自 Nacos 共享配置 `auth.yml`（与签发端「端 BFF」同源）。**登录态键 = `panoramic:login:{type}:{userId}`**，`type` 取 JWT 的 `type` claim（缺省 `admin`）——键格式是网关 ↔ 端 BFF 的共享契约，不可单边改动。

## 快速验证

```bash
# 查看注册中心服务列表（应看到 gateway、admin、store-bff、goods-center、store）
curl http://localhost:8080/discovery/services

# 经网关访问平台端 BFF（须带管理员登录 token）
curl -H "Authorization: Bearer <token>" http://localhost:8080/admin/goods/categories/tree
```

## 依赖

- Nacos 服务发现（地址/账号由父 POM properties 经 Maven 过滤注入）
- 负载均衡：Spring Cloud LoadBalancer（非 Ribbon）
- Redis（reactive）：登录态校验；JJWT：token 验签。**gateway 不依赖 common / common-auth**，其鉴权是独立实现
