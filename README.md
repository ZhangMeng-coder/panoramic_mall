# 全景商城（Panoramic Mall）

`简体中文 | [English](README.en.md)`

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![Nacos](https://img.shields.io/badge/Nacos-2025.1.0.0-1E9FFF)](https://nacos.io/)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-red)](https://baomidou.com/)
[![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Vite](https://img.shields.io/badge/Vite-7-646CFF?logo=vite&logoColor=white)](https://vite.dev/)

**三角色电商全栈项目**：一套 Spring Cloud 微服务后端（**BFF + 下沉域**）+ 三端互相独立的前端工程（平台管理 / 店主端 / 顾客端）。
页面只经网关访问**端 BFF**，业务域不开放公网路由、不做鉴权；**接口契约先于实现落盘**，并配一个静态检查器在提交前核对「契约表 ↔ 代码 ↔ Feign 客户端」三方一致。

> ⚠ **这是个人作品 / 演示项目，不接真实渠道**：支付是**模拟支付**（只校验「金额 == 订单总额」），短信验证码是**模拟固定码**。取舍一律按「演示观感与工程口径」排序，不按生产完备性。

## 目录

- [项目亮点](#项目亮点)
- [功能一览](#功能一览)
- [系统架构](#系统架构)
- [仓库结构](#仓库结构)
- [技术栈](#技术栈)
- [环境依赖](#环境依赖)
- [快速开始](#快速开始)
- [文档导航](#文档导航)
- [已知边界](#已知边界)

## 项目亮点

**① 分层收口：页面只见端 BFF，域是「哑」的**
三端 BFF（admin / store-bff / mall-bff）持有各自账号表与登录态；四个业务域（goods-center / store / customer-center / trade-center）**不暴露公网路由**（网关白名单强制，域服务一律 403），且结构上**拿不到认证链**——鉴权装配单独放在 `common-auth`，只被端 BFF 依赖。于是「域不鉴权」不是纪律，而是依赖方向决定的。

**② 契约先行，且契约可机器核对**
[`docs/contracts/`](docs/contracts/README.md) 登记**三层契约**（页面级 / 内部 Feign / 跨服务隐式），配套 [`drift-check.mjs`](docs/contracts/drift-check.mjs)：扫描各契约表与源码控制器、Feign 客户端，核对**路径 / 方法 / 权限串 / 条数**，并带**反向哨兵**（防止已删接口悄悄复活）。契约行分「新增」与「修改」两类标记，规则本体写在根 [`CLAUDE.md`](CLAUDE.md)。

**③ 数据作用域只来自登录态**
`customerId` / `storeId` 这类锚点**只能由端 BFF 从登录态取出后写进域入参 DTO**，页面入参里没有该字段（前端传了也不采用）——避免把数据权限交给页面。三套身份（平台管理员 / 店主 / 顾客）id 空间彼此隔离，JWT 带 `userType`，Redis 会话键按端分区。

**④ 最复杂的一条链路（下单）被拆开对待**
请求级幂等（`requestId`）+ 指纹窗口兜底两级幂等、**先占键**记录提交、订单状态机 + 状态轨迹表、库存扣减与回补、**Seata 全局事务**（用例入口 `@GlobalTransactional`，store 域为分支事务）。

**⑤ 域间耦合只有一条边**
`trade-center → store` 是**全仓唯一的域间调用边**（下单流水线的商品快照 / 库存扣减回补），登记在 [cross-cutting 第 24 条](docs/contracts/cross-cutting.md)，并配编译期哨兵（`store-interface` 出现在 trade-center 的 `pom.xml` 里即视为该边存在）。其余跨域协作一律经端 BFF 编排。

**⑥ 同一规则只写一遍**
统计类接口的**窗口算术与分桶归并**收在 `common`（`com.panoramic.common.stats`），域接口只收显式 `start` / `end`、一律按天出点；审计字段（`create_user` / `create_time`…）由 `MyMetaObjectHandler` 经 `UserContext` 自动填充，业务代码禁止手写赋值。这类「违反也不报错」的约定全部成文并逐条登记核对方式。

**⑦ 三端前端统一形态，类型检查就是构建门禁**
Vue 3 + Vite + TypeScript（`strict`）+ axios，三端各自独立工程；`npm run build` = `vue-tsc --noEmit && vite build`——**类型不过即构建失败**，不许为了消错放松门禁（禁 `any` / `@ts-ignore`）。

## 功能一览

> 下面只列**能力面**；逐页行为与字段以各端 README 与 [页面契约](docs/contracts/README.md) 为准，本表不重复。

| 端 | 能力 |
|---|---|
| **平台管理**（[frontend/admin](frontend/admin/) · [backend/admin](backend/admin/)） | 登录 + RBAC（用户 / 角色 / 权限 / 菜单）；标准商品中台维护（分类 / 品牌 / SPU-SKU）；**店铺审核**与**跨店店铺商品管理**（查询 / 详情 / 锁定解锁，平台侧）；订单管理（**只读**全量视角）；**首页数据看板**（用户 / 商家 / 商品 / 营业额 / 成交订单数与比例 / 双折线） |
| **店主端**（[frontend/store](frontend/store/) · [backend/store-bff](backend/store-bff/)） | 店主注册登录；店铺信息与审核状态；在售商品与 SKU（上下架、中台版本比对）；库存管理（含低库存筛选）；订单管理（列表 / 详情 / 发货）；评价管理（列表 / 星级筛选 / 回复）；**首页数据看板**（上架 / 下架 / 库存异常 + 营业额 / 成交 / 评价分布 / 订单折线） |
| **顾客端**（[frontend/mall](frontend/mall/) · [backend/mall-bff](backend/mall-bff/)） | 手机号注册登录（模拟短信）；商品浏览（分类树 / 搜索 / 详情含评分与评价区）；购物车；下单（含幂等与拆单）→ 支付 → 收货 → 评价；我的订单与顾客资料、收货地址 |
| **内部域** | [goods-center](backend/goods-center/) 标准商品平台 · [store](backend/store/) 店铺与在售商品（审核状态机、上下架推导、锁定只读、库存、评价）· [customer-center](backend/customer-center/) 顾客资料与收货地址 · [trade-center](backend/trade-center/) 购物车与订单（DDD 三层，按能力通用、不分端） |

## 系统架构

```
┌───────────────────────────────────────── 前端层 ─────────────────────────────────────────┐
│  admin（管理后台 :5173）    store（商城店铺端 :5174）    mall（商城前台 :5175）          │
└────────────┬─────────────────────────────────┬───────────────────────────────────────────┘
             │  /admin、/store、/mall、/discovery（Vite dev proxy → 网关 8080）
┌────────────▼─────────────────────────────────▼───────────────────────────────────────────┐
│  gateway  API 网关（:8080，Spring Cloud Gateway / WebFlux）                              │
│    公网入口 = 端 BFF 白名单（BffRouteGuardFilter 强制，域服务一律 403）                  │
│      /admin/** ─▶ lb://admin        /store/** ─▶ lb://store-bff                          │
│      /mall/**  ─▶ lb://mall-bff     /discovery/** 探活（不在路由内）                     │
└───────┬─────────────────────────────────────────┬────────────────────────────────────────┘
       │ Nacos 注册发现(:8848)                   │ 内部 Feign（X-User-Id/X-User-Type + 熔断）
┌───────────────────────────────────────────┐  ┌───────────────────────────────────────────┐
│  端 BFF 层（账号表 + type 的 JWT）        │  │  纯域层（不鉴权、无公网路由）             │
│  admin（:8082）平台账号 + RBAC + 编排     │  │  goods-center（:8081）分类/品牌/SPU-SKU   │
│  store-bff（:8084）店主账号 + 商品/订单   │  │  store（:8083）store_shop + 在售商品      │
│  mall-bff（:8085）顾客账号 + 商品/车/订单 │  │  customer-center（:8086）顾客资料/收货地址│
│  三端身份空间彼此隔离，互不调用           │  │  trade-center（:8087）购物车 / 订单       │
└───────────────────────────────────────────┘  └───────────────────────────────────────────┘
              MySQL 8（库 panoramic_mall：goods_* / sys_* / store_* / mall_* / customer_* / trade_*）
```

> `common` 是被所有服务共享的纯基座；鉴权装配（JWT/Redis/安全链）单独放在 `common-auth`，**只有端 BFF 依赖它**——业务域结构上拿不到认证链，因此不鉴权、不碰登录态（⚠ **`trade-center` 是唯一加载 `datasource-redis.yml` 的域**，只用它做购物车加购去重与计数缓存、MySQL 仍是唯一事实源，见 [docs/contracts/cross-cutting.md](docs/contracts/cross-cutting.md) 第 12 条）。业务域与端 BFF（admin / store-bff / mall-bff）之间经**各域自己的 `<域>-interface` 契约模块**（`goods-center-interface` / `store-interface` / `customer-center-interface` / `trade-center-interface`，包根 `com.panoramic.contract.<域>`：同源 Feign 客户端 + DTO/VO）互调，只透传身份头 + 熔断降级，域内不做权限判断。三套身份（平台管理员 / 店主 / C 端顾客）的登录会话键与审计字段格式见 [docs/contracts/cross-cutting.md](docs/contracts/cross-cutting.md) 第 5 / 8 条。⚠ **首页热门商品列表仍为静态 mock**。

## 仓库结构

| 目录 | 说明 | 文档 |
|---|---|---|
| [backend/](backend/) | 微服务后端（Maven 多模块） | [README](backend/README.md) |
| ├── [common/](backend/common/) | 公共基座（非服务）：返回结构/基类/异常/分页/审计自动填充/登录用户模型（域契约类型归各 `<域>-interface`） | [README](backend/common/README.md) |
| ├── [common-auth/](backend/common-auth/) | 鉴权装配层（非服务）：JWT + Redis 登录态 + 安全过滤链；**只被端 BFF 依赖**，业务域拿不到（故不鉴权） | [README](backend/common-auth/README.md) |
| ├── [gateway/](backend/gateway/) | API 网关（8080）：路由转发、前缀剥离、鉴权透传、端 BFF 白名单 | [README](backend/gateway/README.md) |
| ├── [goods-center/](backend/goods-center/) | 商品域（8081，下沉纯域）：标准商品中台 | [README](backend/goods-center/README.md) |
| ├── [store/](backend/store/) | 店铺域（8083，下沉纯域）：店铺 store_shop + 审核状态机 + 在售商品 store_goods_* + 商品评价 store_goods_evaluation（商品 / 店铺评分冗余列由域内刷新） | [README](backend/store/README.md) |
| ├── [customer-center/](backend/customer-center/) | 顾客域（8086，下沉纯域）：顾客资料 customer_profile + 收货地址 customer_address | [README](backend/customer-center/README.md) |
| ├── [trade-center/](backend/trade-center/) | 交易域（8087，下沉纯域）：购物车 trade_cart_item + 订单 trade_order_*（DDD 三层，按能力通用、不分端） | [README](backend/trade-center/README.md) |
| ├── [store-bff/](backend/store-bff/) | 店铺端 BFF（8084）：店主账号 store_user + 店铺资料/在售商品编排 + 本店订单（分页 / 详情 / 发货）+ 本店评价（分页 / 星级筛选 / 回复）+ 首页看板 | [README](backend/store-bff/README.md) |
| ├── [mall-bff/](backend/mall-bff/) | 商城前台 BFF（8085）：C 端顾客账号 mall_user（手机号 + 模拟短信验证码，签发 type=user）+ C 端商品浏览 / 购物车 / 订单编排（内部 Feign → goods-center / store / customer-center / trade-center） | [README](backend/mall-bff/README.md) |
| ├── [admin/](backend/admin/) | 平台管理（8082，端 BFF）：登录 + 用户/角色/权限 + 店铺审核 + 店铺商品管理 + 订单管理（平台只读）+ 首页看板 | [README](backend/admin/README.md) |
| └── [nacos-config/](backend/nacos-config/) | **Nacos 共享配置的版本源**（数据源 / Redis / 鉴权 / 熔断 / Seata 五个 data-id，人工发布到配置中心） | [README](backend/nacos-config/README.md) |
| [frontend/](frontend/) | 前端（按项目拆分；三端统一 Vue 3 + Vite + TypeScript + axios） | [README](frontend/README.md) |
| ├── [admin/](frontend/admin/) | 后端管理后台（5173）：分类/品牌/SPU、用户/角色/权限、店铺审核与店铺商品管理、订单管理（只读）、首页数据看板 | [README](frontend/admin/README.md) |
| ├── [store/](frontend/store/) | 商城店铺端（5174）：店主注册登录 + 店铺信息 + 在售商品管理 + 订单管理（列表 / 详情 / 发货）+ 评价管理（列表 / 星级筛选 / 回复）+ 首页数据看板 | [README](frontend/store/README.md) |
| └── [mall/](frontend/mall/) | 商城前台（5175）：账号 + 商品浏览（搜索 / 分类 / 详情含评分与评价区）+ 购物车 + 我的订单（列表 / 详情 / 支付 / 评价商品）+ 顾客资料与收货地址，均接 mall-bff；首页热门商品列表仍静态 | [README](frontend/mall/README.md) |
| [docs/contracts/](docs/contracts/) | **对外契约清单**（跨前后端）：页面级 / 内部 Feign / 跨服务隐式三层契约 + 静态漂移检查器 | [README](docs/contracts/README.md) |

## 技术栈

- **后端**：Java 21 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 · Nacos · MyBatis-Plus 3.5.16 · MySQL 8 · Seata（AT 模式，仅下单链路）· Lombok
- **前端**：Vue 3 · Vite 7 · Vue Router 4 · **TypeScript（三端统一，`strict`）** · Element Plus（管理后台 / 店铺端）· **ECharts**（两端首页看板）· **Axios**（三端统一 HTTP 客户端，各自 `src/api/request.ts` 单入口）
  - 三端工程各自独立，写法与构建门禁统一（类型检查挂进构建、`tsconfig` 口径、禁 `any` 等），细节见 [frontend/README.md](frontend/README.md)
  - 管理后台 / 店铺端共用一套设计令牌；商城前台是**另一套**（C 端促销风橙红），见 `frontend/mall/src/styles/tokens.css`
- **对外页面接口包 `RespData{code,msg,data}`，内部域接口不包**（形状与错误映射见 [docs/contracts/cross-cutting.md](docs/contracts/cross-cutting.md) 第 1 / 2 条）

## 环境依赖

| 组件 | 地址 | 说明 |
|---|---|---|
| JDK | 21 | 编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | ≥ 20.19 | 前端构建（Vite 7 要求） |
| Nacos | `127.0.0.1:8848`（nacos/nacos） | 服务注册与发现 + **共享配置**（五个 data-id，源文件在 [backend/nacos-config/](backend/nacos-config/)；`spring.config.import` 不带 `optional:`，缺任一即启动失败） |
| MySQL | 连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供 | 库 `panoramic_mall`；主机 / 端口 / 账号 / 密码可用环境变量 `MYSQL_HOST/MYSQL_PORT/MYSQL_USERNAME/MYSQL_PASSWORD/MYSQL_DB` 覆盖 |
| Redis | `localhost:6379` | 登录态缓存 + 购物车去重（键前缀与分区见 cross-cutting 第 5 条） |
| Seata TC | `127.0.0.1:8091`（直连 grouplist） | **只有 `trade-center` 与 `store` 加载**（下单链路的全局事务）；不需要时把 `seata.yml` 的 `seata.enabled` 改 `false` 即可让两个服务退回「无全局事务」 |

## 快速开始

```bash
# 0. 前置：JDK 21 / Maven 3.9+ / Node ≥ 20.19；Nacos 与 MySQL 可用，
#    并把 backend/nacos-config/ 下 5 个 data-id 发布到 Nacos 控制台

# 1. 后端：先安装父 POM 与工具包，再按依赖顺序起服务
cd backend
mvn -N install
mvn -pl common,common-auth,goods-center-interface,store-interface,customer-center-interface,trade-center-interface install
mvn -pl gateway spring-boot:run          # 网关 8080（还需 goods-center/admin/store/store-bff/mall-bff/customer-center/trade-center）

# 2. 前端：三端各自独立安装与启动（Vite dev proxy 转发到网关 8080）
cd ../frontend/admin && npm install && npm run dev   # → http://localhost:5173
```

> 📖 **完整步骤不在这里重抄**：建库建表脚本、服务启动顺序与配置覆盖见 [backend/README.md](backend/README.md)；Nacos 共享配置的发布方式见 [backend/nacos-config/README.md](backend/nacos-config/README.md)；前端各端的启动 / 构建 / 类型检查见 [frontend/README.md](frontend/README.md)。

## 文档导航

| 你想知道 | 去哪看 |
|---|---|
| 某条接口的路径 / 形状 / 权限串 | [`docs/contracts/`](docs/contracts/README.md)（**页面契约的唯一裁决点**，前端只照表写） |
| 跨服务的隐式约定（返回结构、错误映射、作用域、熔断、统计口径…） | [cross-cutting.md](docs/contracts/cross-cutting.md)（逐条含「为什么 / 定义位置 / 破坏后果 / 核对方式」） |
| 某个服务持哪些表、边界在哪、不做什么 | 各模块 README 的「一、架构位置 / 二、实体标记 / 三、职责与边界」 |
| 开发流程与验证口径（需求 → 契约 → 实现） | 根 [CLAUDE.md](CLAUDE.md) |

## 已知边界

- **不接真实渠道**：支付为模拟（校验金额与订单总额一致）、短信验证码为模拟固定码；无第三方登录、无物流对接。
- **首页热门商品列表仍是静态 mock**（`frontend/mall`），其余 C 端页面均已接 mall-bff。
- **部署形态是单机**（Nacos / MySQL / Redis / Seata TC / 网关 / 三个前端同机），未做容器化与多实例。
- **暂无 CI 流水线**：门禁在本地执行 —— 后端「编译通过」、前端 `vue-tsc --noEmit && vite build`、提交前 `node docs/contracts/drift-check.mjs`（契约漂移检查）。
- 平台端对订单**只读**（不在管理后台代客操作订单）；退款恒为**全额**，无部分退款口径。
- 部分口径差异是**刻意**的，已在文档里点名（如店主端看板「库存异常」与库存页「仅看低库存」不是同一条件），改动前请先读对应 README 的「注意事项」。
