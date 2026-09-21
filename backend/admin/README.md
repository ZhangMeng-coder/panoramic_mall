# admin — 平台管理端 BFF

全景商城**平台管理后端**（Servlet 技术栈），端口 **8082**，服务前端 `frontend/admin`（5173），网关公网路由 `/admin/** → lb://admin`。

本服务是 `@PreAuthorize` 授权的**唯一位置**（只有平台管理员有 RBAC）。职责分两块：**平台账号与 RBAC**（自持 `sys_*` 表，不经任何下游）+ **业务编排**（商品模板、店铺管理、店铺商品管理，全部下沉到域服务）。

## 一、架构位置

**端 BFF（第 ① 层）**：公网唯一入口是网关；本层是平台管理前端唯一可见的接口面。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | 平台管理前端，经网关 `/admin/**`（`StripPrefix=1`） | HTTP，返回 `RespData` |
| 本层调谁 | goods-center(8081) | Feign `GoodsCenterClient`：分类 / 品牌 / 标准 SPU-SKU 模板的 CRUD、分类树、分类全路径 |
| 本层调谁 | store(8083) | Feign `StoreClient`（**platform 侧**方法）：店铺分页/详情/审核、店铺商品跨店分页/详情/锁定/解锁、店铺下拉 |
| 与谁**互不调用** | store-bff（店铺端） | 两端身份空间隔离（D2）；**admin 不读店主账号**（D6） |

全部下游调用经 `common` 的 `BffFeignCall` 包装（剥 cause 链还原业务异常 / 其余降级）。本层依赖 `common-auth` 做鉴权——业务域只依赖 `common`，结构上拿不到这条链。

两个主要编排类：`ShopGoodsBffService`（店铺商品）、`StoreShopBffService`（店铺管理）。

### 模块边界（谁持什么）

| 内容 | 归属 |
|---|---|
| 平台账号、角色、权限、角色-权限、用户-角色（`sys_*` 5 张表） | **admin（本模块）** |
| 平台登录与登录态（JWT `type=admin` + Redis 会话） | **admin（本模块）** |
| 动态菜单（当前用户侧栏）与按钮权限串 | **admin（本模块）** |
| 标准商品模板（分类/品牌/SPU/SKU） | goods-center（内部 Feign） |
| 店铺资料与审核状态机、店铺在售商品 | store 域（内部 Feign） |
| 分类**子树展开**、分类**全路径**解析、锁定人渲染 | **admin（本模块编排）**，域不持分类表、不做树操作 |

> 📋 对外接口清单见 [`docs/contracts/admin.md`](../../docs/contracts/admin.md)（条数与落地状态以该表为准，本 README 不另记）。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 说明 |
|---|---|
| `sys_user` | 平台账号（BCrypt 密码） |
| `sys_role` | 角色 |
| `sys_permission` | 权限（多级树：`type` 1=目录 / 2=页面 / 3=按钮；`perms` 权限串；`route` 前端路由，仅页面型） |
| `sys_user_role` | 用户-角色（纯关联表，**无审计列、物理删除**） |
| `sys_role_permission` | 角色-权限（纯关联表，**无审计列、物理删除**） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，含**幂等权限种子**）。⚠ 建库只有一个入口：历次结构变更（审计列 `create_user/update_user` 改 `VARCHAR(32)` 等）的**最终形状**都已写进该文件，不再保留中间迁移脚本。

⚠ **角色授权不由本文件播种**：`schema.sql` 只种 `sys_permission` 权限项（目录 / 页面 / 按钮），`sys_role_permission` 的授权一律在「角色管理 → 分配权限」UI 里勾选。即**换一台新库建好表后，还需手工授权才能登录使用**——这是一处已知缺口，暂不在 `schema.sql` 里补种子。

实体沿用 common `BaseEntity`（逻辑删除 + 审计字段自动填充，取值格式见 `CLAUDE.md`「代码生成与分层约定」）。⚠ 两张关联表按仓库约定**除外**（纯关联、无审计列、物理删除）。

### 权限种子的 id 约定

**目录 X → 页面 X1 → 按钮 X11+**（例：顶级目录 `4 店铺管理` → 页面 `41 店铺列表`（带路由 `/shop`，`perms=store:shop`）→ 按钮 `查询/审核`）。新增页面时沿用该编号法，别另起一套。

## 三、职责与边界

### 1. 平台账号与 RBAC

- 账号密码登录（BCrypt），JWT + Redis 会话，签发 `type=admin`
- 用户 / 角色 / 权限的增删改查；权限树维护（3 层：目录-页面-按钮）
- **角色-权限分配**与**用户-角色分配**（两张关联表）
- **动态菜单**：按当前登录用户的角色过滤权限树，返回其可见的侧栏菜单 —— 属登录后必得数据，**不设权限串**
- 权限串与前端 `v-perm` 指令配套控制按钮显隐

### 2. 商品模板管理（编排）

- 分类 / 品牌 / 标准 SPU-SKU 模板的页面接口，全部下沉到 goods-center
- ⚠ **本层只编排，不持域实体**；模板无价格/库存，语义见 [`../goods-center/README.md`](../goods-center/README.md)

### 3. 店铺管理与店铺商品管理（编排）

- **店铺管理**：店铺列表 / 详情、**通过 / 驳回（填原因）**，权限串 `store:shop:list` / `store:shop:audit`；**不下发、不展示店主登录账号**
- **店铺商品管理**（2026-09-12 新增）：全店铺在售商品列表 + 只读详情 + **平台锁定 / 解锁**，权限串 `store:goods:list` / `store:goods:lock`

本层负责的三件编排工作：

| 编排项 | 口径 |
|---|---|
| **分类子树匹配** | 页面只传**单个** `categoryId`，本层取 goods-center 分类树展开为「该节点 + 全部后代」的 `categoryIds` 再传给域（**域只做 `IN`**，不持分类表） |
| **分类全路径** | 读时调 goods-center 批量路径接口补 `categoryPath`（如「服饰 / 男装 / T恤」）；**失败只告警、路径留空**，前端回退快照名——展示增强不得拖垮主流程 |
| **锁定人渲染** | 库里存 `admin:{id}` 原串；前端渲染为「平台管理员(N)」，**不做 `sys_user` 联查取名**（D6：admin 不读店主账号，同一口径） |

### 4. 边界（本层不做什么）

- **不持域实体、不落域表**：本层的 5 张表全是自己的 RBAC 表；分类/品牌/SPU/店铺/店铺商品数据全在下游域
- **不做数据归属判断**：域内 owner/platform 分流由「调哪一侧接口」决定，本层调 platform 侧、由 `@PreAuthorize` 把关
- **不直接改上下架推导量**：锁定时的级联下架由 store 域内 `refreshShelfStatus` 完成，本层只提交锁定意图
- **不做业务校验兜底**：域内业务校验失败（400/403/404）经 `InternalApiErrorDecoder` 还原后**原样透传**给页面，不吞、不改写
- **不做域的服务发现直连**：只经 Feign 客户端，**不带任何内部令牌**（该信任头已删除，域不做鉴权）

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供，默认指向 `123.56.117.17:3306`（root/root，库 `panoramic_mall`）；连接其他库请注入环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见该共享配置，账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：`datasource-mysql.yml` / `datasource-redis.yml` / `auth.yml` / `feign-circuitbreaker.yml`。import **不带 `optional:`**——缺任一则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
- **熔断**：`resilience4j.circuitbreaker.configs.default.ignore-exceptions` 必须含 `com.panoramic.common.exception.ServiceException`（业务 4xx 不计失败率），否则店主/管理员连续几次操作失误就会打开熔断、把后续**正常**请求降级成 500。语义见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 13 条
- `@EnableFeignClients` 扫描 `com.panoramic.contract.goods` 与 `com.panoramic.contract.store`
- 响应结构：成功 `code=200`；业务失败 `code=400` 携带中文提示；系统异常 / 下游不可用 `code=500`
- 前端 `frontend/admin`（5173）经 Vite dev proxy 走网关
