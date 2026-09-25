# mall-bff — 商城前台 BFF（C 端顾客端）

全景商城**商城前台 BFF**（Servlet 技术栈），端口 **8085**。
服务**C 端顾客**（`frontend/mall`），网关公网路由 `/mall/** → lb://mall-bff`。

它是本仓库的**第三个端 BFF**（另两个是 admin / store-bff）：只持**顾客账号 `mall_user`**，
签发**第三套身份** `type=user`。

> ⚠ **服务范围 = 顾客账号骨架**（取码 / 注册 / 登录 / 登出 / me / 换绑手机号）+ **顾客资料**（读合并进 me、写走 `PUT /profile`）+ **C 端商品浏览**（分类树 / 商品分页 / 筛选聚合 / 详情）+ **购物车**（加购 / 列表 / 计数 / 改数量 / 选中 / 删除 / 清空）+ **订单**（下单 / 列表 / 详情 / 支付 / 确认收货 / 待支付改收货地址 / 待支付取消 / 已支付未发货仅退款）+ **评价**（提交 / 商品评价分页 / 星级分布；回复只在商户端）。
> 首页「**热门商品列表**」区块仍是**静态 mock**（`frontend/mall/src/mock/`）。

## 一、架构位置

**端 BFF（第 ① 层）**：公网唯一入口是网关；本层是商城前台前端可见的接口面。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | 前台前端，经网关 `/mall/**`（`StripPrefix=1`） | HTTP，返回 `RespData` |
| 本层调谁 | **goods-center**（分类树，8081）、**store**（商品分页 / 筛选聚合 / 详情 / 批量详情 / 评价读写，8083）、**customer-center**（顾客资料与收货地址，8086）与 **trade-center**（购物车与订单，8087） | Feign + 熔断降级（`BffFeignCall`） |
| 与谁**互不调用** | admin / store-bff | 三端身份空间彼此隔离 |

`@EnableFeignClients` 扫四个包：`com.panoramic.contract.store`（`StoreClient`）、`com.panoramic.contract.goods`（`GoodsCenterClient`）、
`com.panoramic.contract.customer`（`CustomerCenterClient`）与 `com.panoramic.contract.trade`（`TradeCenterClient`），
编排集中在 `service/CatalogBffService`（商品浏览）、`service/CustomerProfileBffService`（顾客资料，含默认昵称规则）、`service/CartBffService`（购物车）、`service/OrderBffService`（订单）与 `service/EvaluationBffService`（评价）；
Feign 出参 DTO 与域侧**同源于该域的 `<域>-interface` 模块**（`contract.store.*` / `contract.goods.*` / `contract.customer.*` / `contract.trade.*`），本模块不复制一份。

本层依赖 [common-auth](../common-auth/) 做鉴权；业务域只依赖 `common`，结构上拿不到这条链。

### 模块边界（谁持什么）

| 内容 | 归属 |
|---|---|
| 顾客账号 `mall_user`（取码/注册/登录/登出/me，JWT+Redis，`type=user`，**无 RBAC**） | **mall-bff（本模块）** |
| 分类树 | goods-center（内部 Feign） |
| 在售商品分页 / 筛选聚合 / 详情 | store 域（跨店通用侧；**C 端展示口径由本层固定传参**） |
| 顾客资料与收货地址 | customer-center（内部 Feign）。⚠ 「有没有地址 / 默认是哪条（id）」这个**派生态**由**本层**从地址列表派生并缓存（Redis，见第四节）——它是下单前的分支依据，**不是**地址数据 |
| 地址状态缓存（`GET /addresses/status` 的回参） | **mall-bff（本模块）**。⚠ 本层除登录态外唯一直接用的 Redis 键：权威仍是 customer-center 的地址表，缓存只是它的派生物，靠四个写路径失效 + TTL 兜底 |
| 购物车行（顾客 / 数量 / 选中态） | **trade-center**（内部 Feign，8087）。⚠ 商品名 / 图 / 规格 / 价格 / 库存**仍属 store 域**：本层读购物车时按 `spuId` 批量补详情，购物车表里**没有商品快照** |
| 订单（单号 / 状态 / 金额 / 条目 / 收货地址快照） | **trade-center**（内部 Feign，8087）。⚠ **地址快照由本层取**：下单时先经 customer-center 取地址、校验归属，再组快照传给域（域结构上调不到 customer-center）。待支付改地址走**同一条**取快照路径；⚠ 它**不动地址簿**，故**不**失效上面那条地址状态缓存 |
| 评价（评分 / 文字 / SKU 快照 / 商家回复） | **store 域**（内部 Feign，8083）——`store_goods_evaluation` + 两张表的 `score` 冗余列。⚠ 本层只**编排**：**订单门禁在 BFF**（经 trade-center 取单判「已收货」，不为它新增跨域边）、SKU 快照从订单明细归组、昵称头像经 customer-center 批量补 |

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

实体沿用 common `BaseEntity`（逻辑删除 + 审计字段自动填充，取值格式见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 8 条）。

> ⚠ **`password` 是占位列**：C 端走短信验证码登录，本期不读写该列；留着是为了与 `store_user` 形状一致，将来加密码登录/改密时不需迁移表。

## 三、职责与边界

### 1. 登录与登录态

- 免鉴权白名单**只有 4 条且逐条登记在两处**（网关 + 服务侧）：`/auth/sms-code`、`/auth/register`、`/auth/login` 与首页宫格分类树 `/catalog/categories`。⚠ 分类树那条是**精确路径不是前缀**——`/catalog/goods`、`/catalog/facets`、`/catalog/goods/{id}` **一律要顾客登录态**（鉴权分级见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 11 条）
- 签发 `type=user` 的 JWT 并写 Redis 顾客登录上下文（键格式与跨端隔离见 [common-auth/README.md](../common-auth/README.md)）；本地认证链由网关注入的 `X-User-Id` + `X-User-Type` → Redis 重建登录顾客
- **顾客端不接 RBAC**：登录后对自己的数据全权限，因此本层**没有一个 `@PreAuthorize`**（这是预期状态，不是漏登记）

### 2. 账号模型

**账号即手机号**（`phone` 既是登录账号、也是唯一键；导出到快照与 `CurrentUserVO` 时与 `username` 同值），验证方式为**手机号 + 短信验证码、无密码**——短信是**模拟通道**：只打日志、校验固定码 `888888`（契约本身，非临时实现）。

> 错误码、校验顺序、资料读写口径、换绑口径等**逐条登记在 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md) 的「形状与行为口径」**，本 README 不重复；
> **免鉴权路径与鉴权分级**的落点是上一条与 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 11 条。

⚠ **默认昵称（「用户」+ 手机号后 4 位）只在写入侧实现一处**：`CustomerProfileBffService#withDefaultNickname`——
注册成功时、以及 `PUT /profile` 昵称留空时各调它一次（后者不放它写成 NULL，否则「清空昵称」会重新制造无昵称用户）。
**读取侧不拼后 4 位**（那需要手机号，而手机号只在本端）：评价区拿不到顾客资料时统一下发占位「用户」。

### 3. 边界（本层不做什么）

- **不持业务域实体、不落域表**：本层只有 `mall_user` 一张表
- **只做编排、不持域数据**：调域走 `store-interface` 的 `StoreClient`、`goods-center-interface` 的 `GoodsCenterClient`、`customer-center-interface` 的 `CustomerCenterClient` 与 `trade-center-interface` 的 `TradeCenterClient`，编排集中在 `CatalogBffService` / `CustomerProfileBffService` / `CartBffService` / `OrderBffService`；
  降级统一走 `common` 的 `BffFeignCall`（下游故障 → 「…暂不可用」，业务 4xx 原样透传给页面）；
  分类树对分页 / facets 只是**增强**（子树展开、筛选名解析），拿不到就降级为「无树」，不拖垮主流程
- **C 端商品可见性只在本层判、且只有一处实现**：`CatalogBffService#isVisible` 的两个出口
  `#visibleDetailOrNull`（详情 / 加购）与 `#visibleSpuIds`（购物车列表）——列表把三条件当查询参数传给域，
  详情与购物车行**取回后重判**（见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 20 条）。
  ⚠ **别再写第三份判定**；购物车行的不可见**不 404、也不删行**，打 `invalid` 标记后照常下发
- **不做身份类型判断**：`type` claim 由签发端携带、全链路透传；网关只验签 + 查登录态，域服务不判身份
- **评价只编排、不自持**：评价数据（含商品 / 店铺评分）全在 store 域，本层做的是**域做不到的三件编排**——
  订单门禁（经 trade-center 取单判「已收货」，**不为它新增 `store → trade` 的跨域边**，与「店铺审核门禁在端 BFF」同一先例）、
  按 `spuId` 归组 SKU 快照（规格 / 单价 / 数量只能来自订单明细）、昵称头像经 customer-center **批量**补齐。
  落点只有一处 `service/EvaluationBffService`（订单详情的「已评价」标记也从它取，**静默降级**）。
  ⚠ 评价 / 回复文字**按纯文本渲染**（不接 `HtmlSanitizer`、前端不许 `v-html`）——清洗那条口径是为
  「店主自由录入 HTML」这个前提存在的，评价没有这个前提。逐条口径见 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md) 第二节评价那几条

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供；连接其他库请注入环境变量 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：`datasource-mysql.yml` / `datasource-redis.yml` / `auth.yml` / `feign-circuitbreaker.yml`。import **不带 `optional:`**——缺任一则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
  - ⚠ 本层**已接域调用**（goods-center + store + customer-center + trade-center，共四个），`feign-circuitbreaker.yml` **不再是空转**——「端 BFF 一律加载四个」的规律不变
- **`config/JacksonConfig` 不是可选项**：Boot 4 的 web starter 不再自动注册 `ObjectMapper`，而 `LoginUserCacheService` 要注入一个用于读写 Redis 登录快照——缺此 bean 服务直接起不来
- 本地白名单 `panoramic.auth.whitelist-paths: /auth/login,/auth/register,/auth/sms-code,/catalog/categories`（覆盖 `SecurityConfig` 只含 `/auth/login` 的默认值；⚠ 分类树那条是**精确路径**不是前缀，理由见上节）
- 模拟短信固定码 `panoramic.mall.sms-fixed-code: 888888`
- 地址状态缓存（键前缀 `panoramic.mall.address-status-redis-prefix`，默认 `panoramic:mall:addr-status`；TTL `panoramic.mall.address-status-ttl-seconds`，默认 1800）：两个键都**有默认值**，只在要调时才写进配置。⚠ 它是**派生**缓存，读失败按未命中、写 / 失效失败不报错（TTL 兜底），读失败时**不写**缓存——口径见 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md) 的「地址状态缓存」
- 响应结构：成功 `code=200`；业务校验失败 `code=400` 携带中文提示；账号停用 `code=515`
