# store-bff — 店铺端 BFF（店主端）

全景商城**店铺端 BFF**（Servlet 技术栈，由原 `store-center` 拆出，2026-09-07），端口 **8084**。
服务**店主端（frontend/store）**，网关公网路由 `/store/** → lb://store-bff`。

本模块只持**店主账号 `store_user`**（注册/登录/签发，`type=store`）；店铺数据（`store_shop` + 审核状态机）归 **store 域**，经注册中心内部 Feign（`StoreClient` → `/internal/store/**`）编排。与 admin（平台端）**互不调用**（D2）。

## 模块边界

| 内容 | 归属 |
|---|---|
| 店主账号 `store_user`（注册/登录/登出/me，JWT+Redis，type=store，无 RBAC） | store-bff（本模块） |
| 店铺 `store_shop` + 审核状态机（owner：mine/save/submit） | store 域（内部 Feign） |
| 平台店铺管理（platform：page/detail/audit） | admin BFF → store 域（与本模块无关） |

## 接口清单（对外，经网关统一加 `/store` 前缀，服务内无前缀，均返回 `RespData`）

### 店主认证（`/auth`，网关与服务两侧白名单 `/auth/login,/auth/register`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册（注册即登录）→ `RespData<LoginResultVO>` |
| POST | `/auth/login` | 登录 → `RespData<LoginResultVO>` |
| POST | `/auth/logout` | 登出（删 Redis 店主登录上下文） |
| GET | `/auth/me` | 当前店主信息 |

### 店主店铺（`/shops`，需店主登录；对外形状与旧 store-center 一致，前端不改路径）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/shops/mine` | 我的店铺（未开店返回 data=null，前端据 null 进「未开店」引导） |
| POST | `/shops/save` | 保存草稿（无店则建 id=账号id 的店） |
| POST | `/shops/submit` | 提交审核（完整资质校验） |

> 「账号店同 ID」：当前店主**账号 id 即 store_id**，mine/save/submit 以账号 id 作 store_id 调 store 域 owner 接口（无需按 owner 反查我的店）。

## 编排与降级

- `StoreShopBffService`（`com.panoramic.storebff.bff`）只做编排：持 `StoreClient`，`call(Supplier)` 统一执行——下游**业务异常（400 参数/业务，如「审核中锁定」「提交前请补全」）原样透传**由 common 统一异常处理还原 `RespData` 给页面；**熔断/连接/序列化等降级**为「店铺服务暂不可用，请稍后重试」（resilience4j 参数见 `application.yml`，仿 admin BFF）。
- `StoreClient` 与共享 DTO/VO 上移 common（`com.panoramic.common.store`，同源一份）；出站**只**原样透传 `X-User-Id`/`X-User-Type`（**不做 goods 版「缺省回退 admin」写死兜底**，避免店主侧被盖成 admin）；**不再带 `X-Internal-Token`**（该信任头已于 2026-09-10 删除——store 域不鉴权）。
- 熔断降级文案在 BFF 统一处理，店铺域下游故障不拖垮店主端。

## 数据库（库：`panoramic_mall`，与 store 域同库、只分表所有权）

| 表 | 说明 |
|---|---|
| `store_user` | 店主账号（username/password BCrypt/nickname/phone/status）；id 即其店铺主键（账号店同 ID） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）；审计列改造见 `db/migrate-audit-usertype.sql`（2026-09-10）。字段沿用 common `BaseEntity` 约定：逻辑删除 + 创建/更新时间与操作人——操作人 `create_user`/`update_user` 为 **`VARCHAR(32)`**，值为 **`UserType:UserId`**（本模块写入的为 `store:{id}`）。

## 鉴权说明

- 本模块是**端 BFF**，依赖 `common-auth`（`JwtService` / `LoginUserCacheService` / `SecurityConfig` / `AuthTokenFilter`）做鉴权；业务域只依赖 `common`，结构上拿不到这条链。
- 店主登录/注册走白名单（网关 `/store/auth/login,register` + 服务侧 `/auth/login,/auth/register`），签发 `type=store` 的 JWT 并写 Redis 店主登录上下文，**键 = `panoramic:login:store:{userId}`**（各端身份空间隔离，2026-09-10 起）
- `/shops/**` 走 `common-auth` 本地认证链（JWT/网关注入的 `X-User-Id` + `X-User-Type` → Redis 按 `store:{id}` 重建登录店主），store 店主无 RBAC，登录后对自己店全权限
- `@EnableFeignClients(basePackages = "com.panoramic.common.store")` 扫描内部 Feign 客户端

## 配置说明

- **数据源**：默认本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程/定制库请注入环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见 Nacos `datasource-mysql.yml`，账号密码勿写入代码或提交到仓库）
- Nacos 共享配置：`datasource-mysql.yml` / `datasource-redis.yml` / `auth.yml`（jwt-secret/redis-prefix/header-name 由端 BFF 与 gateway 同源；域服务不引入后两者）
- 响应结构：成功 `code=200`；业务校验失败 `code=400` 携带中文提示；下游 store 域不可用统一 `code=500`「店铺服务暂不可用」
