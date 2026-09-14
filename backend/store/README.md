# store — 店铺业务域（下沉纯域）

全景商城**店铺业务域**（Servlet 技术栈，2026-09-07 由原 `store-center` 拆分而来），端口 **8083**。

本域持**店铺资料 `store_shop`（含审核状态机）** 与 **店铺在售商品 `store_goods_spu` / `store_goods_sku`**，不带店主登录、不带页面编排。

## 一、架构位置

**下沉纯域（第 ② 层）**：不向页面暴露公网路由，只被各端 BFF 经注册中心**内部 Feign** 调用。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | store-bff（**owner 侧**：我的店铺、店铺商品） | `common` 的 `StoreClient`，带熔断降级 |
| 被谁调 | admin BFF（**platform 侧**：店铺管理审核、店铺商品跨店管理与锁定） | 同上 |
| 本域调谁 | — | **不启用 Feign 客户端，纯被调方** |

拆分后的归属边界：

- 店主账号 `store_user` 归 **store-bff**（见 [`../store-bff/README.md`](../store-bff/README.md)）
- 本域**不持店主账号**，平台侧也不与店主账号联查（D6）
- 本域只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链与 Redis
- ⚠ 域端口只在内网可达是**安全前提**：本域不做鉴权，防线在网络层，不在应用层

> 📋 对外接口清单（18 条，owner / platform 两侧）见 [`docs/contracts/store.md`](../../docs/contracts/store.md)。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 归属 | 说明 |
|---|---|---|
| `store_shop` | **store（本域）** | 店铺（主键 = 店主账号 id + 资质字段 + 审核状态/留痕字段） |
| `store_goods_spu` | **store（本域）** | 店铺在售商品 SPU（中台关联 `goods_spu_id` + 版本戳快照 `center_version` + `shelf_status` + 平台锁定 `lock_status/lock_reason/lock_user/lock_time`） |
| `store_goods_sku` | **store（本域）** | 店铺在售商品 SKU（规格组合 + 编码 + 图片 + `price`；**无库存列**） |
| `store_user` | store-bff | 店主账号（见 store-bff schema，**不在本域**） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行；含为存量库补锁定列的幂等守卫块）。⚠ 建库只有一个入口：审计列形状、平台锁定四列 + `idx_lock_status` 的**最终形状**都已写进该文件，不再保留中间迁移脚本。

字段沿用 common `BaseEntity` 约定：逻辑删除 + 创建/更新时间与操作人（MP 自动填充）——操作人 `create_user`/`update_user` 为 **`VARCHAR(32)`**，值为 **`UserType:UserId`**（如 `store:5`）；`audit_by` 是审核人留痕列（平台管理员 id），维持 `BIGINT UNSIGNED` 不变。

## 三、职责与边界

### 1. 数据权限模型

- **账号店同 ID（一人一店）**：`store_shop.id == 店主账号 id`（`IdType.INPUT`，建店时由 store-bff 带入账号 id）；已删除 `owner_user_id` 列与 `uk_owner_user_id`，天然一人一店。
- **store_id 通用数据权限（D5）**：owner 侧方法必带 `store_id`、只作用于「store_id == 传入值」的行；platform 侧方法不带 `store_id`、全量。
  - 在售商品（`store_goods_*`）**owner 侧以「id + store_id」双条件取行**（`StoreGoodsSpuServiceImpl#getOwnedOrThrow`）：他人商品与不存在的商品**同样报「商品不存在」**，不泄露存在性；SKU 不持 `store_id`，先校验其 SPU 归属再操作。
  - **owner / platform 的分流由「哪个 BFF 调哪一侧接口」决定，域内不做身份断言**（原 `assertOwner` / `requirePlatformAdmin` 已删）：store-bff 调 owner 侧并从登录态取 store_id，admin 调 platform 侧并由 `@PreAuthorize` 把关。
  - `audit_by` 直取 `X-User-Id` 仅留痕（不与平台账号联查，D6）。

### 2. 店铺审核状态机

审核状态：**0 草稿 → 1 待审核 → 2 已通过 / 3 已驳回**（店主可编辑重提）。

| 状态 | 保存草稿(owner save) | 提交(owner submit) | 平台审核(platform audit) |
|---|---|---|---|
| 无店铺(id=store_id 无行) | 建草稿(0) | 提交(1) | — |
| 0 草稿 | 更新草稿(0) | 提交(1) | — |
| 1 待审核 | ❌ 审核中锁定 | ❌ 重复提交 | ✅ 通过(2)/驳回(3) |
| 2 已通过 | ❌ 信息锁定只读 | ❌ 无需提交 | — |
| 3 已驳回 | 回到草稿(0) 清留痕 | 重新提交(1) | — |

- **提交即校验完整资质**：联系人/电话/省市区+详细地址/营业执照名称/统一社会信用代码/执照照 均必填；保存草稿不强制
- **审核只对「待审核(1)」做条件更新**（`update ... where status=1`）：并发/重复审核时更新 0 行即拒绝，防重复审核
- 驳回必须填原因（`audit_remark`）；审核人/审核时间（`audit_by`/`audit_time`）仅留痕记录，不与平台用户表联查
- 编辑驳回回草稿/驳回重提时把提交/审核留痕列显式写 NULL（MP update 默认跳过 null 列，实体置空不够）
- ⚠ **审核门禁不在域内做**：店铺 `status == 2`（已通过）的判断由端 BFF 编排时前置

### 3. 店铺在售商品

字段与中台标准商品同构，SKU 额外带 `price`（**不建库存列**）；分类/品牌存「id 引用 + 名称快照」，保存时**不回查中台**。

上下架规则收敛为一句话：**SPU 上架 ⟺ 至少一个 SKU 上架**（不变量，无独立 SPU 上下架入口）。

分类**全路径**（如「服饰 / 男装 / T恤」）**域内不解析**——域不持分类表，由端 BFF 读时调 goods-center 批量路径接口补全（本域只保证快照名可用）。

| # | 规则 |
|---|---|
| R1 | 新增：SPU 与全部 SKU 一律下架态落库；每个 SKU `price` 必填且 ≥0.01 |
| R2/R3 | 上架任一 SKU → SPU 自动上架；SKU 全下架 → SPU 自动下架（`refreshShelfStatus` 是唯一写入口） |
| R4 | 已上架 SKU **整行锁死**：规格组合/价格/编码/图片不可改、不可删（整单替换时缺行即拒绝），须先下架 |
| R5 | 未上架 SKU 可增、可改、可删 |
| R6 | 存在上架 SKU 时 `spec_config` 只读（防 SKU 组合孤儿） |
| R7 | 名称/主图/轮播图/详情/分类/品牌任何时候都可改 |
| R8 | 存在上架 SKU 时拒绝删除 SPU；否则软删 SPU 并**级联软删**其下全部 SKU |
| R9 | 审核门禁（`status == 2`）**不在域内做**，由端 BFF 前置 |
| R10 | 中台版本同步（比对 `center_version`、给「同步」按钮）由端 BFF 编排，域只存版本快照 |
| R11 | owner 侧方法入口以「id + store_id」限定作用域（platform 侧方法不带 store_id、跨店全量） |
| R12 | **平台锁定**（2026-09-12 新增）：锁定 → 名下 SKU 全部级联下架、SPU 随之推导为下架；锁定期 owner 侧整行只读；仅平台可解锁，解锁不自动恢复上架 |

> ⚠ `refreshShelfStatus` 是「SPU 上架 ⟺ ≥1 SKU 上架」的**唯一写者**。任何时候都不要绕过它直接改 `shelf_status`——包括锁定时的级联下架。

### 4. 平台锁定规则（R12，2026-09-12 新增）

管理后台「店铺商品管理」可对**任意店铺**的商品锁定/解锁（`lock_status` 列，见 `db/schema.sql` 中 `store_goods_spu` 的列定义与文件尾部的幂等补列块）。

| 项 | 口径 |
|---|---|
| 锁定写入 | `lock_status=1` + `lock_reason`（必填）+ `lock_user` + `lock_time`；已锁定则拒绝重复操作（条件更新 `where lock_status=0`，防并发） |
| 自动下架 | 锁定时把名下**已上架** SKU 批量置下架，再由 `refreshShelfStatus` 推导 SPU 为下架——**不变量不变，仍是唯一写者**，不绕过它直接改 `shelf_status` |
| 锁定期 owner 侧 | **整行只读**：编辑 / 删除 / SKU 整单替换 / SKU 上下架 一律拒绝（`assertNotLocked`，域内强制，不只靠前端禁用按钮），提示「商品已被平台锁定，不可 X，请联系平台管理员」 |
| 解锁 | 清空 `lock_reason`/`lock_user`/`lock_time`、`lock_status=0`（**必须 `lambdaUpdate().set(null)` 显式清**，`updateById` 跳过 null 会清不掉）；**不动 SKU 与 `shelf_status`**——保持下架，由店主手动重新上架 |
| 锁定人取值 | `UserContext` 直取，按审计同格式存 `UserType:UserId`（如 `admin:1`）。这是**业务列而非审计列**（D7），故在 service 内显式写入；**店铺端不展示锁定人**，仅管理端展示 |
| 权限 | 平台侧由 admin BFF 的 `@PreAuthorize store:goods:lock` 把关；**域内不做任何权限判断** |

### 5. 边界（本域不做什么）

- **不做任何鉴权、不做任何权限判断、不校验 token**；唯一授权点是调用方端 BFF 的 `@PreAuthorize`
- **不装配认证链**：不打 Redis、不查登录态；`application.yml` 不声明 auth 白名单
- **不做审核门禁、不做版本比对、不解析分类路径**——三者都是调用方 BFF 的编排职责
- **不持店主账号、不持分类表**

### 6. 信任与防线

入口只有两道：

1. `StoreUserIdentityFilter` —— 把透传的 `X-User-Id`/`X-User-Type` 直取填 `UserContext`，供审计填充与 `audit_by` 留痕；**缺头即不填充、放行**，不回 401
2. 本地 `StoreSecurityConfig` —— 唯一一条全放行链，避免 Spring Security 默认链拦截 actuator

原 `InternalTrustFilter`（验 `X-Internal-Token`）与 `assertOwner` / `requirePlatformAdmin`（域内 userType 断言）**均已删除**（2026-09-10）。

> ⚠ 本域**不做鉴权是有意设计，不是疏漏**。安全性完全依赖 `8083` 端口只在内网可达。

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供，默认指向 `123.56.117.17:3306`（root/root，库 `panoramic_mall`）；连接其他库请注入环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见该共享配置，账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置加载**：只引入 `datasource-mysql.yml`，且 import **不带 `optional:`**——配置中心不可用或该 dataId 缺失时启动即失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 11 条
- MyBatis-Plus：主键 `IdType.INPUT`（store_shop.id = 账号 id）、`is_delete` 逻辑删除、驼峰映射
- 启动类扫描 `com.panoramic` 以加载 common 的全局异常处理、分页插件、字段自动填充与安全链
- 异常语义（内部）：经 `StoreDomainExceptionHandler` 还原**真实 HTTP 状态 + `{code,msg}`**，供内部 Feign ErrorDecoder 还原为 `ServiceException`
- 响应结构：**本域内部接口不包 `RespData`**（`RespData` 只用于端 BFF 的对外接口）
