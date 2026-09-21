# trade-center — 交易域（下沉纯域）

全景商城**交易域**（Servlet 技术栈，2026-09-21 新建），端口 **8087**。

本域持**购物车 `trade_cart_item`**。⚠ **本期只有购物车**：订单 / 结账 / 评价**不在本期**
（下单需要跨域事务、库存扣减、支付与状态机，是独立的一期），计划见仓库根 [`todo.md`](../../todo.md)。

> 接口清单与**实现进度不在这份文件里维护**——见 [`docs/contracts/trade-center.md`](../../docs/contracts/trade-center.md)：
> 那张表由 `docs/contracts/drift-check.mjs` 与代码**双向核对**，始终反映真实进度（本 README 里写死条数只会随每次实现失真）。

## 一、架构位置

**下沉纯域（第 ② 层）**：不向页面暴露公网路由，只被端 BFF 经注册中心**内部 Feign** 调用。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | mall-bff（**C 端顾客自助购物车**） | `trade-center-interface` 的 `TradeCenterClient`，带熔断降级 |
| 本域调谁 | — | **不启用 Feign 客户端，纯被调方** |

- 顾客账号 `mall_user`（手机号 / 密码 / 登录态）归 **mall-bff**；顾客资料与收货地址归 **customer-center**；
  店铺商品（SPU / SKU / 价格 / 上下架）归 **store 域**。本域**只记 `spuId` / `skuId` 的 id 引用，不持商品快照**
  （加购时刻的价格到结算时早已过期，任何快照都是错的）
- 本域只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链：**不做鉴权、不碰 token、不查登录态**，
  防线在网络层（8087 端口只在内网可达），不在应用层
- ⚠ 本域**破例**加载了 Redis（见下节），但这不改变上面任何一条：Redis 里没有身份、没有登录态，**它不是鉴权组件**

> 📋 对外接口清单见 [`docs/contracts/trade-center.md`](../../docs/contracts/trade-center.md)。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 归属 | 说明 |
|---|---|---|
| `trade_cart_item` | **trade-center（本域）** | 购物车行（`customer_id` + `spu_id` / `sku_id` + `quantity` + `selected`；**唯一键 `uk_customer_sku (customer_id, sku_id)`**，即同一顾客同一 SKU 至多一行） |
| `mall_user` | mall-bff | 顾客账号（**不在本域**） |
| `customer_profile` / `customer_address` | customer-center | 顾客资料 / 收货地址（**不在本域**） |
| `store_goods_spu` / `store_goods_sku` | store | 店铺商品（**不在本域**；本域只存 id） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行，纯新增）。

实体沿用 common `BaseEntity`（审计字段自动填充，代码不得显式赋值；取值格式见 `CLAUDE.md`「代码生成与分层约定」）。

### 1. 为什么走**物理删除**（本表 `is_delete` 恒 0）

全仓其它实体一律逻辑删除，本表是**刻意的例外**，理由是它与本表的唯一键**互斥**：

- 逻辑删除只把 `is_delete` 置 1，**行仍在表里、仍参与唯一键**；
- 于是「删掉某 SKU → 再加回同一 SKU」这种最普通的操作，插入会直接撞
  `uk_customer_sku (customer_id, sku_id)` → 用户看到的是「删了以后再也加不回来」。

故（用户已裁定）购物车走物理删除，`is_delete` 列**恒 0**，只为对齐 `BaseEntity` 的 `@TableLogic` 而保留。
⚠ 随之而来的纪律：**本表禁止调用 `removeById` / `remove(...)` / `IService#remove*`**（那是逻辑删除，
会把「再加购」变成 500）——删行只走 `TradeCartItemMapper` 的三个 `physicalDelete*`。
唯一键本身是「同一 SKU 至多一行」这个不变量的承担者：重复加购由它收敛成一条 UPDATE（累加数量），
并发首插也由它收敛成「一个成功、一个改走自增」。

## 三、职责与边界

### 1. 数据权限模型

- **锚点 `customerId` = `mall_user.id`**（跨域 id 引用、**无外键**）：域内所有方法**全按传入锚点过滤**，`customerId` 就是数据权限本身。
- **无 owner / platform 分侧**：本期只做 C 端自助，没有平台侧能力，故不像 store 那样由「BFF 调哪一侧」分流。
- **域内不做任何身份判断**：不判 `X-User-Type`、无 `@PreAuthorize`；调用方传的 `customerId` 是否「本人」**由 mall-bff 从登录态取**，域侧不校验——防线在 BFF。

### 2. 上限口径

| 维度 | 上限 | 超限 | 封顶位置 |
|---|---|---|---|
| 单行数量 `quantity` | ≤ 999 | 400（插入路径）/ 静默封顶（累加路径） | 插入路径由 DTO 的 `@Max(999)` 拦；累加路径由自增 SQL 的 `LEAST(quantity + delta, 999)` 在 SQL 里拦 |
| 单购物车行数 | ≤ 100 行 | 400「购物车最多 100 种商品，请先清理」 | 只在**确实要走插入**时判（累加既有 SKU 不受「车满了」影响，否则用户连同一件商品都加不了）。⚠ **软上限**：并发下两笔可双双通过检查而略微越界，同 customer-center 的 20 条地址上限口径 |

⚠ **数量上限必须两条路径各封一次**：只封插入路径的话，反复加购同一 SKU 就能把 `quantity` 顶到 INT 上限。

### 3. 为什么本域**破例**加载 Redis（全仓唯一的域服务）

两个键，**都是性能优化**，不是事实源：

| 键 | 类型 / TTL | 角色 |
|---|---|---|
| `panoramic:cart:skus:{customerId}` | Set（成员=skuId 字符串）/ 24h | 加购的**第一遍重复过滤**：命中即走原子自增，省掉一次「先查后插」。⚠ 只放 id、**绝不放数量**（放了就有两个数值源、就有分歧） |
| `panoramic:cart:count:{customerId}` | String（值=行数）/ 60s | 购物车**行数的读穿透缓存**。⚠ **不许用 `INCRBY` 之类的算式维护**（漏算一次就永久偏掉）：口径一律「**写路径 DEL、读路径穿透**」，最坏只是少一次命中，**不会算错** |

- ⚠ **这不是把鉴权下沉**：Redis 里没有登录态、没有 token、没有权限快照，本域仍然不鉴权、不查登录态。
  它是 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 12 条「Nacos 加载矩阵」的**登记例外**
  （其余域服务都不引 `datasource-redis.yml`）。
- **MySQL 是唯一事实源**，两个键与库数据**无强一致要求**：Redis 不可用时 `CartRedisCache` 的所有方法
  **吞异常 + `log.warn` 降级**（读按未命中、写当没写过），**绝不让加购因为 Redis 失败**。
- **两处失效都能自愈**（两个方向的错判都只影响「走哪条支路」，不影响结果）：

| 失效方向 | 触发场景 | 自愈路径 |
|---|---|---|
| **误报**（Set 里有、库里没这行） | 行被并发删除 / Set 里是上次清空前的残留 | 原子自增匹配 **0 行** → **回落到插入** |
| **漏报**（Set 里没有、库里其实有） | Set 过期、Redis 曾不可用、两笔并发首插 | 插入撞**唯一键** `DuplicateKeyException` → **回落到原子自增** |

- 计数缓存另有一层**已知的短暂不一致**（设计上接受，故它只用于角标）：写路径先落库、再 DEL 键，
  中间若有并发读，它会用**提交前**的值回写该键，于是该值可能在 TTL（60 秒）内偏掉，随后自愈。

### 4. 「0 行受影响」的两种口径（刻意不一致，别整齐化）

| 支路 | 0 行的含义 | 处理 |
|---|---|---|
| 按 id 改单行（改数量 / 改选中） | 「这行不存在或不属于你」——与「改成功」是**两件事** | **抛 404**，不回假成功（并发删除时页面必须知道这次改动没落到任何行上） |
| 批量删 / 清空 / 全选 | 「这些行现在都不在车里了」——正是调用方要的结果 | **幂等**，0 行不报错（多选删除不该因某行被另一个标签页并发删掉而整批失败） |

两条都与 customer-center 的同名口径一致（`CustomerAddressServiceImpl#requireOwned` / `deleteAddress`）。

### 5. 边界（本域不做什么）

- **不做任何鉴权、不做权限判断、不校验 token**；唯一授权点是 mall-bff 从登录态取 `customerId`
- **不装配认证链**：不打 Redis 登录态、不查 token；`application.yml` 不声明 auth 白名单
- **不判商品可见性**：店铺是否已审核 / SPU 是否上架 / 是否被平台锁定**都在 mall-bff 读时重判**，
  域只按 `spuId` / `skuId` 出原始行（见 cross-cutting 第 20 条：购物车行是「C 端可见性」不变量的**第三个落点**）。
  同理，全选是**域侧整表**操作，作用面含 BFF 眼里「已失效」的行
- **不加购时校验商品存在性**：域不持商品、不调 store（加购不做跨域调用）；商品下架 / 不存在由 BFF 与页面处理
- **不做订单 / 结账 / 评价**（本期外，见 [`todo.md`](../../todo.md)）
- **不做页面编排**（商品详情拼装、失效标记、汇总金额都在 mall-bff）

### 6. 信任与防线

入口只有两道：

1. `TradeUserIdentityFilter` —— 把透传的 `X-User-Id`/`X-User-Type` 直取填 `UserContext`，供审计填充；**缺头即不填充、放行**，不回 401
2. 本地 `TradeSecurityConfig` —— 唯一一条全放行链，避免 Spring Security 默认链拦截 actuator

> ⚠ 本域**不做鉴权是有意设计，不是疏漏**。安全性完全依赖 `8087` 端口只在内网可达。
> `customerId` 直接取自请求路径且域侧不判归属——「锚点即数据权限」这条口径只在
> 「路径上的 `customerId` 由 mall-bff 从登录态填」+「8087 不可从公网抵达」**同时**成立时才成立。

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供；**Redis** 由 `datasource-redis.yml` 提供
  （`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD` 可覆盖）。连接其他库请注入环境变量
  `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DB`/`MYSQL_USERNAME`/`MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置加载**：引入 `datasource-mysql.yml` **与** `datasource-redis.yml`，且 import **不带 `optional:`**
  ——缺任一 dataId 则启动失败。加载矩阵见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 12 条
- MyBatis-Plus：主键 `IdType.AUTO`（`trade_cart_item.id` 自增）；**本表 `is_delete` 恒 0**（物理删除，
  见上文「为什么走物理删除」）；驼峰映射
- 启动类扫描 `com.panoramic` 以加载 common 的全局异常处理、分页插件、字段自动填充与安全链
- 异常语义（内部）：经 `TradeDomainExceptionHandler` 还原**真实 HTTP 状态 + `{code,msg}`**，供内部 Feign
  ErrorDecoder 还原为 `ServiceException`。⚠ **兜底 `Exception` → HTTP 500 这一形状不得改**（4xx/5xx 分野是熔断契约，
  见 cross-cutting 第 13 条）
- 响应结构：**本域内部接口不包 `RespData`**（`RespData` 只用于端 BFF 的对外接口）
