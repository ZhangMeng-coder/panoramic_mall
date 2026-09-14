# mall-bff — 商城前台 BFF（C 端顾客端）

全景商城**商城前台 BFF**（Servlet 技术栈），端口 **8085**。
服务**C 端顾客**（`frontend/mall`），网关公网路由 `/mall/** → lb://mall-bff`。

它是本仓库的**第三个端 BFF**（另两个是 admin / store-bff）：只持**顾客账号 `mall_user`**，
签发**第三套身份** `type=user`，Redis 登录态键 `panoramic:login:user:{userId}`。

> ⚠ **一期范围 = 顾客账号骨架**（取码 / 注册 / 登录 / 登出 / me）——**不调任何业务域**。
> 首页数据聚合（商品 / 分类）是**二期**，届时才引入 Feign 客户端。

## 一、架构位置

**端 BFF（第 ① 层）**：公网唯一入口是网关；本层的 `/auth/**` 就是**前端唯一可见的接口面**。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | 前台前端，经网关 `/mall/**`（`StripPrefix=1`） | HTTP，返回 `RespData` |
| 本层调谁 | **一期：无**（`@EnableFeignClients` 未启用，`com.panoramic.common.mall` 包不存在） | — |
| 二期计划 | goods-center(8081) 的商品 / 分类，做首页聚合 | Feign + 熔断降级 |
| 与谁**互不调用** | admin / store-bff | 三端身份空间彼此隔离 |

本层依赖 `common-auth`（`JwtService` / `LoginUserCacheService` / `SecurityConfig` / `AuthTokenFilter`）做鉴权——业务域只依赖 `common`，结构上拿不到这条链。

### 模块边界（谁持什么）

| 内容 | 归属 |
|---|---|
| 顾客账号 `mall_user`（取码/注册/登录/登出/me，JWT+Redis，`type=user`，**无 RBAC**） | **mall-bff（本模块）** |
| 商品 / 分类 / 品牌 | goods-center（**一期未接入**） |
| 购物车 / 订单 / 评价 | 未来的 `trade-center`（不存在） |

> 📋 对外接口清单（5 条）见 [`docs/contracts/mall-bff.md`](../../docs/contracts/mall-bff.md)。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其他端同库，**只分表所有权**）。

| 表 | 说明 |
|---|---|
| `mall_user` | 顾客账号（phone / password 占位 / nickname / status）；**phone 即登录账号**，唯一键 `uk_phone` |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。⚠ 建库只有一个入口，不保留中间迁移脚本。

字段沿用 common `BaseEntity` 约定：逻辑删除 + 创建/更新时间与操作人——操作人 `create_user`/`update_user` 为 **`VARCHAR(32)`**，值为 **`UserType:UserId`**（本模块写入的为 `user:{id}`）。

> ⚠ **`password` 是占位列**：C 端走短信验证码登录，本期不读写该列；留着是为了与 `store_user` 形状一致，将来加密码登录/改密时不需迁移表。

## 三、职责与边界

### 1. 登录与登录态

- 取码 / 注册 / 登录走白名单（网关 `/mall/auth/sms-code,register,login` + 服务侧 `/auth/sms-code,/auth/register,/auth/login`，**两处都要登记**）
  - ⚠ `/auth/sms-code` 在**登录之前**被调用（「获取验证码」按钮），漏登记则取码直接 401
- 签发 `type=user` 的 JWT 并写 Redis 顾客登录上下文，**键 = `panoramic:login:user:{userId}`**
- 本地认证链：JWT / 网关注入的 `X-User-Id` + `X-User-Type` → Redis 按 `user:{id}` 重建登录顾客
- **顾客端不接 RBAC**：登录后对自己的数据全权限，因此本层**没有一个 `@PreAuthorize`**（这是预期状态，不是漏登记）

### 2. 账号模型与验证方式

| 项 | 口径 |
|---|---|
| **账号即手机号** | `phone` 既是登录账号、也是唯一键；`username` 不是独立列——导出到 `LoginUser` 快照与 `CurrentUserVO` 时，`username` 与 `phone` **同值**（取自快照，**不查库**） |
| 验证方式 | 手机号 + **短信验证码**，无密码 |
| ⚠ 短信为**模拟实现** | 取码只 `log.info` 一行，**不真发短信、不落库、不落 Redis**；校验一律与 `panoramic.mall.sms-fixed-code`（默认 `888888`）比对。接真实短信服务只需替换 `AuthService#sendSmsCode` 与 `AuthService#assertCode` 两处 |
| 错误码 | 验证码错误 `400`；手机号已注册 `400`；手机号未注册 `400`；账号停用 `USER_DISABLED`（`515`） |
| 校验顺序 | 注册：验码 → 手机号查重 → 建号；登录：验码 → 查账号 → 查状态 |

### 3. 边界（本层不做什么）

- **不持业务域实体、不落域表**：本层只有 `mall_user` 一张表
- **一期不调任何域**：没有 Feign 客户端、没有编排、没有降级逻辑
- **不做身份类型判断**：`type` claim 由签发端携带、全链路透传；网关只验签 + 查登录态

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供；连接其他库请注入环境变量 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：`datasource-mysql.yml` / `datasource-redis.yml` / `auth.yml` / `feign-circuitbreaker.yml`。import **不带 `optional:`**——缺任一则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 11 条
  - ⚠ 一期**没有 Feign 客户端**，`feign-circuitbreaker.yml` 属**空转**——仍按「端 BFF 一律加载四个」的规律引入：配置是惰性的、不产生副作用，二期接域调用时无需再改
- **`config/JacksonConfig` 不是可选项**：Boot 4 的 web starter 不再自动注册 `ObjectMapper`，而 `LoginUserCacheService` 要注入一个用于读写 Redis 登录快照——缺此 bean 服务直接起不来
- 本地白名单 `panoramic.auth.whitelist-paths: /auth/login,/auth/register,/auth/sms-code`（覆盖 `SecurityConfig` 只含 `/auth/login` 的默认值）
- 模拟短信固定码 `panoramic.mall.sms-fixed-code: 888888`
- 响应结构：成功 `code=200`；业务校验失败 `code=400` 携带中文提示；账号停用 `code=515`
