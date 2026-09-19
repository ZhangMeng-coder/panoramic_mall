# customer-center — 顾客域（下沉纯域）

全景商城**顾客域**（Servlet 技术栈，2026-09-19 新建），端口 **8086**。

本域持**顾客资料 `customer_profile`** 与**收货地址 `customer_address`**，不带顾客登录、不带页面编排。

> 接口清单与**实现进度不在这份文件里维护**——见 [`docs/contracts/customer-center.md`](../../docs/contracts/customer-center.md)：
> 那张表由 `docs/contracts/drift-check.mjs` 与代码**双向核对**，始终反映真实进度（本 README 里写死条数只会随每次实现失真）。
> 契约按行分批落地：摘掉某行的 `待实现` 标记 + 声明 Feign 方法 + 补域实现，**三者同一提交**。
> 下表第三条的落库口径是本批次的实现口径。

## 一、架构位置

**下沉纯域（第 ② 层）**：不向页面暴露公网路由，只被端 BFF 经注册中心**内部 Feign** 调用。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | mall-bff（**C 端顾客自助**：资料、收货地址） | `customer-center-interface` 的 `CustomerCenterClient`，带熔断降级 |
| 本域调谁 | — | **不启用 Feign 客户端，纯被调方** |

归属边界：

- 顾客账号 `mall_user`（手机号 / 密码 / 登录态）归 **mall-bff**（见 [`../mall-bff/README.md`](../mall-bff/README.md)）
- 本域**不持顾客账号**、不校验 token、不与账号表联查
- 本域只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链与 Redis
- ⚠ 域端口只在内网可达是**安全前提**：本域不做鉴权，防线在网络层，不在应用层

> 📋 对外接口清单见 [`docs/contracts/customer-center.md`](../../docs/contracts/customer-center.md)。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 归属 | 说明 |
|---|---|---|
| `customer_profile` | **customer-center（本域）** | 顾客资料（昵称 / 头像 / 性别 / 生日；**主键 = `mall_user.id`，一对一**，`IdType.INPUT` 显式插入，不依赖 DB 自增） |
| `customer_address` | **customer-center（本域）** | 收货地址（收件人 / 电话 / 省市区 / 详细地址 / `is_default`；`idx_customer_default (customer_id, is_default)`） |
| `mall_user` | mall-bff | 顾客账号（见 mall-bff schema，**不在本域**） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行，纯新增）。

字段沿用 common `BaseEntity` 约定：逻辑删除 + 创建/更新时间与操作人（MP 自动填充）——`create_user`/`update_user` 为 **`VARCHAR(32)`**，值为 **`UserType:UserId`**（如 `user:42`）。

## 三、职责与边界

### 1. 数据权限模型

- **锚点 `customerId` = `mall_user.id`**（跨域 id 引用、**无外键**）：域内所有方法**全按传入锚点过滤**，`customerId` 就是数据权限本身。
- **无 owner / platform 分侧**：本期只做 C 端自助，没有平台侧能力，故不像 store 那样由「BFF 调哪一侧」分流。
- **域内不做任何身份判断**：不判 `X-User-Type`、无 `@PreAuthorize`；调用方传的 `customerId` 是否「本人」**由 mall-bff 从登录态取**，域侧不校验——防线在 BFF。

### 2. 落库口径（本批次实现口径）

| # | 口径 |
|---|---|
| C1 | **账号与资料拆表**：手机号/密码留 `mall_user`（mall-bff），昵称/头像/性别/生日落 `customer_profile`（本域）；资料行随注册首建（主键 = 账号 id） |
| C2 | **省市区 = 自由文本单列**（`region`，与 `store_shop.region` 同口径）——**不建地区表**、不做省市区三级联动数据 |
| C3 | **默认地址唯一**：同一顾客至多一条 `is_default=1`，由应用层在**同一事务内**先清位再置位；**删掉默认地址后不自动递补**（其余地址保持非默认） |
| C4 | **设默认只有两条路径**：首条地址自动设为默认、`setDefaultAddress`——故 `CustomerAddressSaveDTO` **不含 `isDefault`**（新增/编辑不能直接传） |
| C5 | 审计字段（`create_user`/`update_user`/`create_time`/`update_time`）**一律由 MP 自动填充**，代码不得显式赋值 |

### 3. 边界（本域不做什么）

- **不做任何鉴权、不做权限判断、不校验 token**；唯一授权点是 mall-bff 从登录态取 `customerId`
- **不装配认证链**：不打 Redis、不查登录态；`application.yml` 不声明 auth 白名单
- **不持顾客账号、不持商品与订单**（商品属 store 域、购物车/订单属 trade 域）
- **不做页面编排**（`/auth/me` 资料读增强、可售性校验等都在 mall-bff）

### 4. 信任与防线

入口只有两道：

1. `CustomerUserIdentityFilter` —— 把透传的 `X-User-Id`/`X-User-Type` 直取填 `UserContext`，供审计填充；**缺头即不填充、放行**，不回 401
2. 本地 `CustomerSecurityConfig` —— 唯一一条全放行链，避免 Spring Security 默认链拦截 actuator

> ⚠ 本域**不做鉴权是有意设计，不是疏漏**。安全性完全依赖 `8086` 端口只在内网可达。

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供（默认指向 `123.56.117.17:3306`，库 `panoramic_mall`）；连接其他库请注入环境变量 `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_DB`/`MYSQL_USERNAME`/`MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置加载**：只引入 `datasource-mysql.yml`，且 import **不带 `optional:`**——配置中心不可用或该 dataId 缺失时启动即失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
- MyBatis-Plus：主键策略**按表**——`customer_profile` = `IdType.INPUT`（主键即账号 id，显式插入）、`customer_address` = `IdType.AUTO`（自增）；`is_delete` 逻辑删除、驼峰映射。
  ⚠ 两表**刻意不同**，新建实体时按「主键是否等于外部锚点」选，别照抄隔壁那张表
- 启动类扫描 `com.panoramic` 以加载 common 的全局异常处理、分页插件、字段自动填充与安全链
- 异常语义（内部）：经 `CustomerDomainExceptionHandler` 还原**真实 HTTP 状态 + `{code,msg}`**，供内部 Feign ErrorDecoder 还原为 `ServiceException`
- 响应结构：**本域内部接口不包 `RespData`**（`RespData` 只用于端 BFF 的对外接口）
