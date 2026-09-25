# 全景商城（Panoramic Mall）

基于微服务架构的电商项目，规划由**前端商城（C 端）**、**后端管理（B 端）**与**微服务后端**三部分组成。当前已完成后端基础设施、**登录与 RBAC 权限体系**、**商品中台**全链路，**商城店铺端（店铺管理 + 在售商品管理 + 订单管理 + 本店评价与回复）**，**商城前台工程**（`frontend/mall`，账号、C 端商品浏览（含评分与评价）、购物车与订单（含评价商品）已接入 mall-bff，首页热门商品列表仍静态）、**商城前台 BFF（mall-bff，C 端顾客账号 + 商品浏览 / 评价 / 购物车 / 订单编排）**与**交易域（trade-center，购物车 + 订单）**。后端已按 **BFF + 下沉域**分层收口：页面只经网关访问端 BFF（admin / store-bff / mall-bff），业务域（goods-center / store / customer-center / trade-center）不开放公网路由，仅由端 BFF 经注册中心内部 Feign 调用——**唯一的域间调用边**是 trade-center 下单流水线经 Feign 调 store 域（见 [docs/contracts/cross-cutting.md](docs/contracts/cross-cutting.md) 第 24 条）。

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
| ├── [store-bff/](backend/store-bff/) | 店铺端 BFF（8084）：店主账号 store_user + 店铺资料/在售商品编排 + 本店订单（分页 / 详情 / 发货）+ 本店评价（分页 / 星级筛选 / 回复） | [README](backend/store-bff/README.md) |
| ├── [mall-bff/](backend/mall-bff/) | 商城前台 BFF（8085）：C 端顾客账号 mall_user（手机号 + 模拟短信验证码，签发 type=user）+ C 端商品浏览 / 购物车 / 订单编排（内部 Feign → goods-center 分类树 / store 商品分页与筛选聚合 / customer-center 顾客资料与收货地址 / trade-center 购物车与订单） | [README](backend/mall-bff/README.md) |
| ├── [admin/](backend/admin/) | 平台管理（8082，端 BFF）：登录 + 用户/角色/权限 + 店铺审核 + 店铺商品管理 + 订单管理（平台只读） | [README](backend/admin/README.md) |
| [frontend/](frontend/) | 前端（按项目拆分；三端统一 Vue 3 + Vite + TypeScript + axios） | [README](frontend/README.md) |
| ├── [admin/](frontend/admin/) | 后端管理后台（5173）：分类/品牌/SPU、用户/角色/权限、店铺审核与店铺商品管理、订单管理（只读） | [README](frontend/admin/README.md) |
| ├── [store/](frontend/store/) | 商城店铺端（5174）：店主注册登录 + 店铺信息 + 在售商品管理 + 订单管理（列表 / 详情 / 发货）+ 评价管理（列表 / 星级筛选 / 回复） | [README](frontend/store/README.md) |
| └── [mall/](frontend/mall/) | 商城前台（5175）：账号 + 商品浏览（搜索 / 分类 / 详情含评分与评价区）+ 购物车 + 我的订单（列表 / 详情 / 支付 / 评价商品）+ 顾客资料与收货地址，均接 mall-bff；首页热门商品列表仍静态 | [README](frontend/mall/README.md) |
| [docs/contracts/](docs/contracts/) | **对外契约清单**（跨前后端）：页面级 / 内部 Feign / 跨服务隐式三层契约 + 静态漂移检查器 | [README](docs/contracts/README.md) |

## 技术栈

- **后端**：Java 21 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 · Nacos · MyBatis-Plus 3.5.16 · MySQL 8 · Lombok
- **前端**：Vue 3 · Vite 7 · Vue Router 4 · **TypeScript（三端统一，`strict`）** · Element Plus（管理后台 / 店铺端）· **Axios**（三端统一 HTTP 客户端，各自 `src/api/request.ts` 单入口）
  - 三端工程各自独立，写法与构建门禁统一（类型检查挂进构建、`tsconfig` 口径、禁 `any` 等），细节见 [frontend/README.md](frontend/README.md)
  - 管理后台 / 店铺端共用一套设计令牌；商城前台是**另一套**（C 端促销风橙红），见 `frontend/mall/src/styles/tokens.css`
- **对外页面接口包 `RespData{code,msg,data}`，内部域接口不包**（形状与错误映射见 [docs/contracts/cross-cutting.md](docs/contracts/cross-cutting.md) 第 1 / 2 条）

## 环境依赖

| 组件 | 地址 | 说明 |
|---|---|---|
| JDK | 21 | 编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | ≥ 20.19 | 前端构建（Vite 7 要求） |
| Nacos | `127.0.0.1:8848`（nacos/nacos） | 服务注册与发现 |
| MySQL | 连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供 | 库 `panoramic_mall`；主机 / 端口 / 账号 / 密码可用环境变量 `MYSQL_HOST/MYSQL_PORT/MYSQL_USERNAME/MYSQL_PASSWORD/MYSQL_DB` 覆盖 |