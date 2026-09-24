# 全景商城后端（panoramic-mall-backend）

全景商城微服务后端，基于 **Spring Boot 4.0.7 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0** 的 Maven 多模块工程，注册中心使用 **Nacos**。

## 模块一览

| 模块 | 类型 | 端口 | 说明 |
|---|---|---|---|
| [common](common/) | 工具包（非服务） | — | 公共基座：通用返回结构、异常处理、实体基类（`BaseEntity`）、审计自动填充（`MyMetaObjectHandler`）、登录用户模型与 `UserContext`、Feign 错误解码与降级包装（`InternalApiErrorDecoder` / `BffFeignCall`）、`HtmlSanitizer`。**所有服务都依赖**；⚠ **不含任何域契约类型** |
| [common-auth](common-auth/) | 工具包（非服务） | — | 鉴权装配层：`SecurityConfig` / `AuthTokenFilter` / `JwtService` / `LoginUserCacheService`（Redis 登录态 + JJWT）。**只被端 BFF（admin / store-bff / mall-bff）依赖**；业务域只依赖 `common`，结构上拿不到认证链与 Redis |
| [goods-center-interface](goods-center-interface/) | 工具包（非服务） | — | 标准商品域**内部契约包**：`GoodsCenterClient`（Feign）+ 同源 DTO/VO（包根 `com.panoramic.contract.goods`）。**被 goods-center 与调用它的端 BFF 共用同一份**（不各抄一份，避免漂移） |
| [store-interface](store-interface/) | 工具包（非服务） | — | 店铺域**内部契约包**：`StoreClient`（Feign）+ 同源 DTO/VO（包根 `com.panoramic.contract.store`）。**被 store 与调用它的端 BFF 共用同一份** |
| [customer-center-interface](customer-center-interface/) | 工具包（非服务） | — | 顾客域**内部契约包**：`CustomerCenterClient`（Feign）+ 同源 DTO/VO（包根 `com.panoramic.contract.customer`）。**被 customer-center 与调用它的端 BFF 共用同一份** |
| [trade-center-interface](trade-center-interface/) | 工具包（非服务） | — | 交易域**内部契约包**：`TradeCenterClient`（Feign）+ 同源 DTO/VO（包根 `com.panoramic.contract.trade`）。**被 trade-center 与调用它的三端 BFF（mall-bff / store-bff / admin）共用同一份** |
| [gateway](gateway/) | 网关服务 | 8080 | Spring Cloud Gateway 响应式网关，统一入口、路由转发与鉴权透传；公网只路由到端 BFF（`/admin`、`/store`、`/mall`），域服务一律 403 |
| [goods-center](goods-center/) | 业务服务（下沉纯域） | 8081 | 标准商品平台：分类 / 品牌 / 标准 SPU-SKU 模板；不暴露公网路由，仅被 admin 等 BFF 内部 Feign 调用 |
| [admin](admin/) | 业务服务（端 BFF） | 8082 | 平台管理：账号登录、RBAC（用户/角色/权限/菜单）、标准商品模板编排、店铺管理审核、**店铺商品管理**（跨店查询/详情/锁定解锁，内部 Feign → goods-center / store）、**订单管理**（平台侧只读列表 / 详情，内部 Feign → trade-center） |
| [store](store/) | 业务服务（下沉纯域） | 8083 | 店铺域：店铺 `store_shop` + 审核状态机 + 店主在售商品 `store_goods_spu` / `store_goods_sku` / `store_goods_sku_stock`（账号店同 ID，id==店主账号 id）；不暴露公网路由，仅被 store-bff / admin / mall-bff 与 **trade-center**（域间协作：下单流水线的商品快照 / 库存扣减与回补，见 cross-cutting 第 24 条）内部 Feign 调用 |
| [store-bff](store-bff/) | 业务服务（店铺端 BFF） | 8084 | 店主端：店主账号 `store_user`（注册即登录、签发 `type=store`）+ 店铺资料编排 + 在售商品与库存编排、中台版本比对（内部 Feign → store / goods-center） |
| [mall-bff](mall-bff/) | 业务服务（商城前台 BFF） | 8085 | C 端顾客：顾客账号 `mall_user`（手机号 + 模拟短信验证码，注册即登录、签发 `type=user`）+ C 端商品浏览（分类树 / 商品分页 / 筛选聚合 / 详情）+ 顾客资料与收货地址 + 购物车 + **订单**（下单 / 列表 / 详情 / 支付 / 确认收货）（内部 Feign → goods-center / store / customer-center / trade-center）。首页**热门商品列表**仍为静态 mock |
| [customer-center](customer-center/) | 业务服务（下沉纯域） | 8086 | 顾客域：顾客资料 `customer_profile` + 收货地址 `customer_address`；不暴露公网路由，仅被 mall-bff 内部 Feign 调用 |
| [trade-center](trade-center/) | 业务服务（下沉纯域） | 8087 | 交易域：购物车 `trade_cart_item` + **订单**（DDD 三层；订单**已落库、三端 BFF 的订单编排均已落地**——**按能力通用、不分端**（订单 9 条 / 购物车 8 条，作用域由各端 BFF 自设，见 [cross-cutting.md](../docs/contracts/cross-cutting.md) 第 22 条），⚠ 仅经内部 Feign 被端 BFF 调用、**不暴露公网路由**；**评价**属后续期（**Seata 全局事务已接入**，2026-09-22 T12：`@GlobalTransactional` 在下单用例入口，store 域为分支事务）。⚠ 全仓**唯一加载 Redis 的域**（加购去重与计数缓存，MySQL 仍是唯一事实源） |

> 📋 **各模块的对外接口清单不在此处，统一登记在 [`docs/contracts/`](../docs/contracts/)** —— 页面级（admin / store-bff / mall-bff）、内部 Feign（goods-center / store / customer-center / trade-center）、跨服务隐式契约（[cross-cutting.md](../docs/contracts/cross-cutting.md)）三层。改动接口时**同一改动内**更新对应契约文件，提交前跑 `node docs/contracts/drift-check.mjs`。
> 各模块 README 只写**服务说明**（职责 / 架构位置 / 实体标记 / 边界），不重复列接口。

## 技术栈

- Java 21、Maven 3.9+
- Spring Boot 4.0.7（`common` / `common-auth` / 各 BFF 与域服务使用 Servlet 技术栈，`gateway` 使用 WebFlux 响应式栈）
- Spring Cloud Gateway、Spring Cloud LoadBalancer、OpenFeign（circuitbreaker 熔断）+ Resilience4j
- Nacos 服务发现与注册（默认 `127.0.0.1:8848`，账号 `nacos/nacos`）
- MyBatis-Plus 3.5.16（`mybatis-plus-spring-boot4-starter`，Spring Boot 4 专用）——逻辑删除 + 字段自动填充 + 分页插件
- MySQL 8（库 `panoramic_mall`；表结构在各模块 `db/schema.sql`：`goods-center` 的 `goods_*`（含版本戳 `version`）、`admin` 的 `sys_*` 权限表 + 权限种子、`store` 的 `store_shop` + `store_goods_spu` / `store_goods_sku` / `store_goods_sku_stock`、`store-bff` 的 `store_user`、`mall-bff` 的 `mall_user`、`customer-center` 的 `customer_profile` / `customer_address`、`trade-center` 的 `trade_cart_item`（**唯一键 `(customer_id, sku_id)` + 物理删除**，是唯一的逻辑删除例外）与订单五表 `trade_order` / `trade_order_item` / `trade_order_status_log` / `trade_order_submission` / `trade_order_submission_order`，均 IF NOT EXISTS 幂等）
- 鉴权：JWT（含 `userType` claim）+ Redis 会话（键 `{前缀}:{userType}:{userId}`），网关验签后向下游透传 `X-User-Id`/`X-User-Type`；`admin` / `store` / `user` 为三套隔离的 id 空间

## 快速开始

### 0. 环境准备

- JDK 21、Maven 3.9+
- Nacos 单机启动（默认 8848，账号 `nacos/nacos`）
- MySQL 8 可用：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供，默认指向 `123.56.117.17:3306`（库 `panoramic_mall`）；连接其他库时通过环境变量注入。⚠ 账号口令只在该共享配置里，勿写入代码或提交到仓库
- **Nacos 共享配置已就位**：把 `nacos-config/` 下 5 个 data-id（`datasource-mysql` / `datasource-redis` / `auth` / `feign-circuitbreaker` / `seata`）发布到 Nacos——服务侧 import **不带 `optional:`**，**该服务加载的**任一缺失即启动失败（各服务加载哪几个见 [cross-cutting.md](../docs/contracts/cross-cutting.md) 第 12 条矩阵）。一览表与发布方式见 [nacos-config/README.md](nacos-config/README.md)

### 1. 构建

```bash
cd backend

# 安装父 POM 与六个工具包（common / common-auth / 四个 -interface）到本地仓库
# （新增/修改了父 POM 或这几个模块后需重跑）
mvn -N install
mvn -pl common,common-auth,goods-center-interface,store-interface,customer-center-interface,trade-center-interface install

# 全量编译打包
mvn clean package
```

### 2. 启动（按依赖顺序，各开一个终端）

> 首次运行前先建库建表：分别执行 `goods-center` / `admin` / `store` / `store-bff` / `mall-bff` / `customer-center` / `trade-center` 下 `db/schema.sql`（IF NOT EXISTS 可重复执行）。各端账号表同库不同表，物理不分开。

```bash
mvn -pl gateway spring-boot:run          # 网关 8080
mvn -pl goods-center spring-boot:run     # 商品域 8081
mvn -pl admin spring-boot:run            # 平台管理（端 BFF）8082
mvn -pl store spring-boot:run            # 店铺域 8083
mvn -pl store-bff spring-boot:run        # 店铺端 BFF 8084
mvn -pl mall-bff spring-boot:run         # 商城前台 BFF（C 端）8085
mvn -pl customer-center spring-boot:run  # 顾客域 8086
mvn -pl trade-center spring-boot:run     # 交易域（购物车 + 订单）8087
```

> 配置说明：
> - `application.yml` 中的 `@nacos.server-addr@` 等占位符由 Maven 资源过滤在构建时替换为父 POM properties 中集中定义的版本/地址。
> - **多服务复用的配置**（数据源 / Redis / 鉴权 / Feign 熔断）收敛在 Nacos 共享配置，服务侧经 `spring.config.import` 引入，**不带 `optional:`**——配置中心不可用或任一 data-id 缺失则启动失败。各服务加载矩阵见 [cross-cutting.md](../docs/contracts/cross-cutting.md) 第 12 条（⚠ `trade-center` 是唯一加载 `datasource-redis` 的域），发布方式见 [nacos-config/README.md](nacos-config/README.md)。

### 3. 验证

```bash
curl http://localhost:8080/discovery/services   # 网关探活：查看已注册服务
```

> goods-center / store / customer-center / trade-center 已下沉纯域，**不再有公网路由**。

## 请求链路

```
admin 前端(5173) ──/admin、/discovery──┐
store 前端(5174) ──/store──────────────┤
mall  前台(5175) ──/mall───────────────┤
                                  gateway(8080)  公网只路由到端 BFF，域服务 403
   /admin/** ─▶ admin(8082, 端BFF)      /store/** ─▶ store-bff(8084, 店铺端 BFF)
   /mall/**  ─▶ mall-bff(8085, C端BFF)
                         │                            │
                         └──── 内部 Feign（身份头 X-User-Id/X-User-Type + 熔断降级）────┘
                               │                              │
             纯域：goods-center(8081) / store(8083) / customer-center(8086) / trade-center(8087)
                                        │
                                        └── Nacos(8848) 服务注册与发现 ──▶ MySQL(库 panoramic_mall)
```
