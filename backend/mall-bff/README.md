# mall-bff — 商城前台 BFF（C 端顾客端）

全景商城**商城前台 BFF**（Servlet 技术栈），端口 **8085**。
服务**C 端顾客**（`frontend/mall`），网关公网路由 `/mall/** → lb://mall-bff`。

它是本仓库的**第三个端 BFF**（另两个是 admin / store-bff）：只持**顾客账号 `mall_user`**，
签发**第三套身份** `type=user`。

> ⚠ **服务范围 = 顾客账号骨架**（取码 / 注册 / 登录 / 登出 / me / 换绑手机号）+ **顾客资料**（读合并进 me、写走 `PUT /profile`）+ **C 端商品浏览**（分类树 / 商品分页 / 筛选聚合 / 详情）+ **购物车**（加购 / 列表 / 计数 / 改数量 / 选中 / 删除 / 清空）。
> 首页「**热门商品列表**」区块仍是**静态 mock**（`frontend/mall/src/mock/`）。

## 一、架构位置

**端 BFF（第 ① 层）**：公网唯一入口是网关；本层是商城前台前端可见的接口面。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | 前台前端，经网关 `/mall/**`（`StripPrefix=1`） | HTTP，返回 `RespData` |
| 本层调谁 | **goods-center**（分类树，8081）、**store**（商品分页 / 筛选聚合 / 详情 / 批量详情，8083）、**customer-center**（顾客资料与收货地址，8086）与 **trade-center**（购物车，8087） | Feign + 熔断降级（`BffFeignCall`） |
| 与谁**互不调用** | admin / store-bff | 三端身份空间彼此隔离 |

`@EnableFeignClients` 扫四个包：`com.panoramic.contract.store`（`StoreClient`）、`com.panoramic.contract.goods`（`GoodsCenterClient`）、
`com.panoramic.contract.customer`（`CustomerCenterClient`）与 `com.panoramic.contract.trade`（`TradeCenterClient`），
编排集中在 `service/CatalogBffService`（商品浏览）、`service/CustomerProfileBffService`（顾客资料）与 `service/CartBffService`（购物车）；
Feign 出参 DTO 与域侧**同源于该域的 `<域>-interface` 模块**（`contract.store.*` / `contract.goods.*` / `contract.customer.*` / `contract.trade.*`），本模块不复制一份。

本层依赖 [common-auth](../common-auth/) 做鉴权；业务域只依赖 `common`，结构上拿不到这条链。

### 模块边界（谁持什么）

| 内容 | 归属 |
|---|---|
| 顾客账号 `mall_user`（取码/注册/登录/登出/me，JWT+Redis，`type=user`，**无 RBAC**） | **mall-bff（本模块）** |
| 分类树 | goods-center（内部 Feign） |
| 在售商品分页 / 筛选聚合 / 详情 | store 域（跨店通用侧；**C 端展示口径由本层固定传参**） |
| 顾客资料与收货地址 | customer-center（内部 Feign） |
| 购物车行（顾客 / 数量 / 选中态） | **trade-center**（内部 Feign，8087）。⚠ 商品名 / 图 / 规格 / 价格 / 库存**仍属 store 域**：本层读购物车时按 `spuId` 批量补详情，购物车表里**没有商品快照** |
| 结账 / 评价 | `trade-center` 的**后续期**（域侧订单已落库、下单接口已就绪；本层订单编排已接入） |

> 📋 对外接口清单见 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md)（**条数与落地状态以该表为准**——本 README 不另记进度，写死条数只会在下次改动时失真）。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其他端同库，**只分表所有权**）。

| 表 | 说明 |
|---|---|
| `mall_user` | 顾客账号（phone / password 占位 / status）；**phone 即登录账号**，唯一键 `uk_phone` |

> ⚠ **`mall_user.nickname` 正在迁出本表**：昵称（及头像 / 性别 / 生日）已改由 customer-center 的
> `customer_profile` 承载（见 [`docs/contracts/customer-center.md`](../../docs/contracts/customer-center.md)），
> **本模块实体已摘掉该字段**——字段留着会在列删除后让查询带上一个不存在的列（运行时报 `Unknown column`，
> 而编译与契约检查都看不见）。⚠ **列本身尚未删除**（`db/schema.sql` 仍声明），将在迁移时移除，届时本行同步删掉。

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。⚠ 建库只有一个入口，不保留中间迁移脚本。

实体沿用 common `BaseEntity`（逻辑删除 + 审计字段自动填充，取值格式见 `CLAUDE.md`「代码生成与分层约定」）。

> ⚠ **`password` 是占位列**：C 端走短信验证码登录，本期不读写该列；留着是为了与 `store_user` 形状一致，将来加密码登录/改密时不需迁移表。

## 三、职责与边界

### 1. 登录与登录态

- 免鉴权白名单**只有 4 条且逐条登记在两处**（网关 + 服务侧）：`/auth/sms-code`、`/auth/register`、`/auth/login` 与首页宫格分类树 `/catalog/categories`。⚠ 分类树那条是**精确路径不是前缀**——`/catalog/goods`、`/catalog/facets`、`/catalog/goods/{id}` **一律要顾客登录态**（鉴权分级见 [CLAUDE.md](../../CLAUDE.md)）
- 签发 `type=user` 的 JWT 并写 Redis 顾客登录上下文（键格式与跨端隔离见 [common-auth/README.md](../common-auth/README.md)）；本地认证链由网关注入的 `X-User-Id` + `X-User-Type` → Redis 重建登录顾客
- **顾客端不接 RBAC**：登录后对自己的数据全权限，因此本层**没有一个 `@PreAuthorize`**（这是预期状态，不是漏登记）

### 2. 账号模型

**账号即手机号**（`phone` 既是登录账号、也是唯一键；导出到快照与 `CurrentUserVO` 时与 `username` 同值），验证方式为**手机号 + 短信验证码、无密码**——短信是**模拟通道**：只打日志、校验固定码 `888888`（契约本身，非临时实现）。

> 错误码、校验顺序、资料读写口径、换绑口径等**逐条登记在 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md) 的「形状与行为口径」**，本 README 不重复；
> **免鉴权路径与鉴权分级**的落点是上一条与 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 11 条。

### 3. 边界（本层不做什么）

- **不持业务域实体、不落域表**：本层只有 `mall_user` 一张表
- **只做编排、不持域数据**：调域走 `store-interface` 的 `StoreClient`、`goods-center-interface` 的 `GoodsCenterClient`、`customer-center-interface` 的 `CustomerCenterClient` 与 `trade-center-interface` 的 `TradeCenterClient`，编排集中在 `CatalogBffService` / `CustomerProfileBffService` / `CartBffService`；
  降级统一走 `common` 的 `BffFeignCall`（下游故障 → 「…暂不可用」，业务 4xx 原样透传给页面）；
  分类树对分页 / facets 只是**增强**（子树展开、筛选名解析），拿不到就降级为「无树」，不拖垮主流程
- **C 端商品可见性只在本层判、且只有一处实现**：`CatalogBffService#isVisible` 的两个出口
  `#visibleDetailOrNull`（详情 / 加购）与 `#visibleSpuIds`（购物车列表）——列表把三条件当查询参数传给域，
  详情与购物车行**取回后重判**（见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 20 条）。
  ⚠ **别再写第三份判定**；购物车行的不可见**不 404、也不删行**，打 `invalid` 标记后照常下发
- **不做身份类型判断**：`type` claim 由签发端携带、全链路透传；网关只验签 + 查登录态，域服务不判身份

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供；连接其他库请注入环境变量 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：`datasource-mysql.yml` / `datasource-redis.yml` / `auth.yml` / `feign-circuitbreaker.yml`。import **不带 `optional:`**——缺任一则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
  - ⚠ 本层**已接域调用**（goods-center + store + customer-center + trade-center，共四个），`feign-circuitbreaker.yml` **不再是空转**——「端 BFF 一律加载四个」的规律不变
- **`config/JacksonConfig` 不是可选项**：Boot 4 的 web starter 不再自动注册 `ObjectMapper`，而 `LoginUserCacheService` 要注入一个用于读写 Redis 登录快照——缺此 bean 服务直接起不来
- 本地白名单 `panoramic.auth.whitelist-paths: /auth/login,/auth/register,/auth/sms-code,/catalog/categories`（覆盖 `SecurityConfig` 只含 `/auth/login` 的默认值；⚠ 分类树那条是**精确路径**不是前缀，理由见上节）
- 模拟短信固定码 `panoramic.mall.sms-fixed-code: 888888`
- 响应结构：成功 `code=200`；业务校验失败 `code=400` 携带中文提示；账号停用 `code=515`
