# 全景商城后端（panoramic-mall-backend）

全景商城微服务后端，基于 **Spring Boot 4.0.7 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0** 的 Maven 多模块工程，注册中心使用 **Nacos**。

## 模块一览

| 模块 | 类型 | 端口 | 说明 |
|---|---|---|---|
| [common](common/) | 工具包（非服务） | — | 公共基座：通用返回结构、异常处理、实体基类（`BaseEntity`）、审计自动填充（`MyMetaObjectHandler`）、登录用户模型与 `UserContext`、各域 Feign 客户端与同源 DTO/VO（`com.panoramic.common.goods` / `com.panoramic.common.store`）。**所有服务都依赖**（业务域不鉴权，故不依赖 `common-auth`） |
| [common-auth](common-auth/) | 工具包（非服务） | — | 鉴权装配层：`SecurityConfig` / `AuthTokenFilter` / `JwtService` / `LoginUserCacheService`（Redis 登录态 + JJWT）。**只被端 BFF（admin / store-bff / 未来 mall-bff）依赖**——业务域只依赖 `common`，结构上拿不到认证链与 Redis |
| [gateway](gateway/) | 网关服务 | 8080 | Spring Cloud Gateway 响应式网关，统一入口、路由转发与鉴权透传；公网只路由到端 BFF（`/admin`、`/store`），域服务一律 403 |
| [goods-center](goods-center/) | 业务服务（下沉纯域） | 8081 | 标准商品平台：分类 / 品牌 / 标准 SPU-SKU 模板；不暴露公网路由，仅被 admin 等 BFF 内部 Feign 调用 |
| [admin](admin/) | 业务服务（端 BFF） | 8082 | 平台管理：账号登录、RBAC（用户/角色/权限/菜单）、标准商品模板编排、店铺管理审核（内部 Feign → goods-center / store） |
| [store](store/) | 业务服务（下沉纯域） | 8083 | 店铺域：店铺 store_shop + 审核状态机 + 店主在售商品 store_goods_spu/store_goods_sku（账号店同 ID，id==店主账号 id）；不暴露公网路由，仅被 store-bff/admin 内部 Feign 调用 |
| [store-bff](store-bff/) | 业务服务（店铺端 BFF） | 8084 | 店主端：店主账号 store_user（注册即登录、签发 type=store）+ 店铺资料编排 + 在售商品编排与中台版本比对（内部 Feign → store / goods-center） |

## 技术栈

- Java 21、Maven 3.9+
- Spring Boot 4.0.7（`common` / `common-auth` / 各 BFF 与域服务使用 Servlet 技术栈，`gateway` 使用 WebFlux 响应式栈）
- Spring Cloud Gateway、Spring Cloud LoadBalancer、OpenFeign（circuitbreaker 熔断）+ Resilience4j
- Nacos 服务发现与注册（默认 `127.0.0.1:8848`，账号 `nacos/nacos`）
- MyBatis-Plus 3.5.16（`mybatis-plus-spring-boot4-starter`，Spring Boot 4 专用）——逻辑删除 + 字段自动填充 + 分页插件
- MySQL 8（库 `panoramic_mall`；表结构在各模块 `db/schema.sql`：`goods-center` 的 `goods_*`（含版本戳 `version`）、`admin` 的 `sys_*` 权限表 + 权限种子、`store` 的 `store_shop` + `store_goods_spu/store_goods_sku`、`store-bff` 的 `store_user`，均 IF NOT EXISTS 幂等）
- 鉴权：JWT（含 `userType` claim）+ Redis 会话（键 `{前缀}:{userType}:{userId}`），网关验签后向下游透传 `X-User-Id`/`X-User-Type`；`admin` 与 `store` 为两套隔离的 id 空间

## 快速开始

### 0. 环境准备

- JDK 21、Maven 3.9+
- Nacos 单机启动（默认 8848，账号 `nacos/nacos`）
- MySQL 8 可用：各服务默认连接本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程库时通过环境变量注入，详见 `goods-center/README.md` 配置说明

### 1. 构建

```bash
cd backend

# 安装父 POM 与 common / common-auth 到本地仓库（新增/修改了父 POM 或这两个模块后需重跑）
mvn -N install
mvn -pl common,common-auth install

# 全量编译打包
mvn clean package
```

### 2. 启动（按依赖顺序，各开一个终端）

> 首次运行前先建库建表：分别执行 `goods-center` / `admin` / `store` / `store-bff` 下 `db/schema.sql`（IF NOT EXISTS 可重复执行）。store 与 store-bff 同库不同表，物理不分开。

```bash
mvn -pl gateway spring-boot:run        # 网关 8080
mvn -pl goods-center spring-boot:run   # 商品域 8081
mvn -pl admin spring-boot:run          # 平台管理（端 BFF）8082
mvn -pl store spring-boot:run          # 店铺域 8083
mvn -pl store-bff spring-boot:run      # 店铺端 BFF 8084
```

> 配置说明：`application.yml` 中的 `@nacos.server-addr@` 等占位符由 Maven 资源过滤在构建时替换为父 POM properties 中集中定义的版本/地址。

### 3. 验证

```bash
curl http://localhost:8080/discovery/services   # 网关探活：查看已注册服务
```

> goods-center / store 已下沉纯域，**不再有公网路由**。页面链路全部改为端 BFF 编排：
> - 商品模板：`GET http://localhost:8080/admin/goods/categories/tree` → 网关 `/admin/**`（StripPrefix=1）→ admin(8082) BFF 编排 → 内部 Feign `/internal/goods/**` → goods-center(8081)
> - 店铺管理：`GET http://localhost:8080/admin/shop/shops` → admin(8082) BFF 编排 → 内部 Feign `/internal/store/**` → store(8083)
> - 店主端：`/store/auth/**`、`/store/shops/**`、`/store/goods/**` → store-bff(8084)（店铺/商品编排内部 Feign → store；分类/品牌下拉与中台模板比对 → goods-center）

## 请求链路

```
admin 前端(5173) ──/admin、/discovery──┐
store 前端(5174) ──/store──────────────┤
                                  gateway(8080)  公网只路由到端 BFF，域服务 403
   /admin/** ─▶ admin(8082, 端BFF)      /store/** ─▶ store-bff(8084, 店铺端 BFF)
                         │                            │
                         └────────── 内部 Feign（身份头 X-User-Id/X-User-Type + 熔断降级） ──────┘
                               │                              │
                    goods-center(8081, 纯域)          store(8083, 纯域, /internal/store/**)
                                        │
                                        └── Nacos(8848) 服务注册与发现 ──▶ MySQL(库 panoramic_mall)
```

## 约定

- 页面/BFF 对外接口统一返回 `RespData{code,msg,data}`，成功 `code=200`；业务失败 `code=400` 携带中文提示；系统异常 `code=500`。**下沉域内部接口不包 RespData**，直接返回业务类型，错误转真实 HTTP 状态 + `{code,msg}`（Feign ErrorDecoder 还原为 `ServiceException`）
- 实体继承 `common` 的 `BaseEntity`（自动填充创建/更新时间与操作人、逻辑删除 `is_delete`），分页查询参数继承 `BasePageVO`。**操作人列为 `VARCHAR(32)`，值 `UserType:UserId`**（如 `admin:1` / `store:7`），类型前缀用于消歧多端身份空间
- 页面只经网关路由到**端 BFF**（`/admin` → admin、`/store` → store-bff）；**业务域服务不暴露公网路由**，只被 BFF 经注册中心内部 Feign 调用（DTO 同源放 common、只透传身份头 `X-User-Id`/`X-User-Type` + 熔断降级，详见 CLAUDE.md「Feign 内部接口规约」与 [goods-center/README.md](goods-center/README.md) / [store/README.md](store/README.md)）
- **鉴权只到端 BFF**：`common-auth`（`SecurityConfig`/`AuthTokenFilter`/`JwtService`/`LoginUserCacheService`）只被 admin / store-bff 依赖，业务域只依赖 `common` → 域服务不装配认证链、不碰 Redis、不做任何权限判断（内部令牌 `X-Internal-Token` 已删除）。⚠ 域端口只在内网可达是前提
- 服务内跨实体只走对方 owner service
- 店主端接口（`/store/auth`、`/store/shops`、`/store/goods`）由 store-bff 登录鉴权后编排到 store 域（owner，store_id=账号 id）；`/store/goods/**` 额外由 BFF 校验「店铺已审核通过」（未过审 `code=403`，域内不做该判断）。平台店铺管理接口（admin 端 `/admin/shop/shops/**`）以 `@PreAuthorize` + 权限串（`store:shop:list/audit`）控制，权限种子见 `admin/db/schema.sql` 与 `backfill-store-permission.sql`；admin 不读店主账号
- 详情见各模块 README：店铺域见 [store/README.md](store/README.md)、店铺端 BFF 见 [store-bff/README.md](store-bff/README.md)。
