# gateway — API 网关

全景商城统一入口网关，基于 **Spring Cloud Gateway（WebFlux 响应式）**，端口 **8080**。全部公网流量唯一入口。

## 一、架构位置

**基础设施层**：不在三层契约之内（既不是端 BFF 也不是域），但它持有**三类契约**——路由、BFF 白名单、鉴权白名单。这三类都是「改了不会编译报错、只会 403 或裸露」的东西。

| 方向 | 对象 | 说明 |
|---|---|---|
| 上游 | 前端（admin / store / 未来的 mall） | 公网 HTTP |
| 下游 | **仅端 BFF**（`lb://` 经 Nacos 服务发现） | 域服务**没有公网路由** |
| 依赖 | Nacos（服务发现 + 共享配置）、Redis（reactive） | **不依赖 `common` / `common-auth`**，其鉴权是独立实现 |

⚠ 「不依赖 common」这一条是重要事实：`LoginUser.CLAIM_USER_TYPE` 这类常量改动**网关不会跟随**，claim 名在网关侧是字面量重写的。

## 二、职责与边界

### 1. 服务路由
将外部请求按路径转发至注册在 Nacos 上的**端 BFF**（`lb://` 负载均衡），并以 `StripPrefix=1` 剥离网关层前缀 —— 网关层前缀与业务服务内部路径**解耦**。

⚠ `StripPrefix=1` 意味着**网关侧路径带前缀、服务侧路径不带**。这条差异贯穿整个白名单体系，也是「两处白名单写法不同」的根因。

### 2. 公网入口白名单
`BffRouteGuardFilter` 强制校验 `panoramic.gateway.bff-services`，**名单外服务（含业务域 goods-center / store）经网关一律 403**。语义要点：**空配置 = 拒绝一切**（默认拒绝，不是放行）；未命中路由直接放行；执行顺序 `-200` **先于**鉴权 `-100`。

域服务只由 BFF 经注册中心内部 Feign 调用，**不给域服务开公网路由**（历史 `/goods/** → goods-center` 已随下沉移除）。

### 3. 鉴权透传
`AuthGlobalFilter` 对非白名单路径：验 JWT 签名/有效期 → 解析 `type` claim → 校验 Redis `panoramic:login:{type}:{userId}` 登录态仍有效 → 注入 `X-User-Id` / `X-User-Type` 给下游。

- 网关只做「验签 + 查登录态 + 注入身份头」，**不做身份类型判断**；身份类型由签发的 `type` claim 携带、全链路透传。
- `jwt-secret` / `jwt-expire-seconds` / `redis-prefix` / `header-name` 来自 Nacos 共享配置 `auth.yml`（与签发端「端 BFF」同源）；`whitelist-paths` 则**各服务本地各自声明**（网关的放行清单形如 `/admin/auth/login`，与端 BFF 的 `/auth/login` 路径形态不同，**不共享，两处都要登记**）。
- **登录态键 = `panoramic:login:{type}:{userId}`**（`type` 取 JWT claim，缺省 `admin`）—— 键格式是网关 ↔ 端 BFF 的共享契约，**不可单边改动**。

### 4. 边界（网关不做什么）
- 不做业务聚合、不持任何业务表、不访问业务库（**无 `datasource-mysql`**）
- 不做权限判定（RBAC 在端 BFF 的 `@PreAuthorize`）
- 不做域服务的服务发现路由

> 📋 路由表（2 条）、BFF 白名单、两处鉴权白名单、探活接口的**完整登记**见 [`docs/contracts/gateway.md`](../../docs/contracts/gateway.md)。
> 本 README 只讲**网关是什么、持哪三类契约、边界在哪**；具体条目一律不在此处重复。

## 三、新增端 BFF 时的检查单

在 `application.yml` 的 `spring.cloud.gateway.server.webflux.routes` 追加路由，并把它加入 `panoramic.gateway.bff-services` 白名单（**两处缺一不可**）；若该端有免鉴权路径，同步登记进网关侧 `whitelist-paths`。

## 四、快速验证

```bash
# 查看注册中心服务列表（应看到 gateway、admin、store-bff、goods-center、store）
curl http://localhost:8080/discovery/services

# 经网关访问平台端 BFF（须带管理员登录 token）
curl -H "Authorization: Bearer <token>" http://localhost:8080/admin/goods/categories/tree
```

## 五、配置说明

- **Nacos**：服务发现（地址/账号由父 POM properties 经 Maven 过滤注入）+ 共享配置 `datasource-redis.yml` / `auth.yml`，import **不带 `optional:`**。**不加载** `datasource-mysql.yml`（网关无数据源）与 `feign-circuitbreaker.yml`（网关不出站调域）。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 11 条
- 负载均衡：Spring Cloud LoadBalancer（非 Ribbon）
- Redis（reactive）：登录态校验；JJWT：token 验签
