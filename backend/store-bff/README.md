# store-bff — 店铺端 BFF（店主端）

全景商城**店铺端 BFF**（Servlet 技术栈，2026-09-07 由原 `store-center` 拆出），端口 **8084**。
服务**店主端（`frontend/store`）**，网关公网路由 `/store/** → lb://store-bff`。

它是本仓库**端 BFF 之一**（另两个是 admin / mall-bff）：只持**店主账号 `store_user`**，负责注册/登录/签发与**跨服务编排**——店铺数据与评价在 store 域、商品分类品牌在中台（goods-center）、订单在交易域（trade-center）、评价人的昵称头像在顾客域（customer-center），本模块自己不落这些表。

## 一、架构位置

**端 BFF（第 ① 层）**：公网唯一入口是网关；本层是店主端前端唯一可见的接口面。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | 店主端前端，经网关 `/store/**`（`StripPrefix=1`） | HTTP，返回 `RespData` |
| 本层调谁 | store 域(8083) | Feign `StoreClient`（店主视角：**作用域取自本层登录态并写进入参 DTO**），带熔断降级 |
| 本层调谁 | goods-center(8081) | Feign `GoodsCenterClient`，带熔断降级 |
| 本层调谁 | trade-center(8087) | Feign `TradeCenterClient`（**本店订单**：分页 / 详情 / 发货；作用域 `storeId` 取自本层登录态），带熔断降级 |
| 本层调谁 | customer-center(8086) | Feign `CustomerCenterClient`（**评价人的昵称 / 头像**，批量一次取回），带熔断降级 |
| 与谁**互不调用** | admin（平台端） | 两端身份空间隔离（D2） |

本层依赖 [common-auth](../common-auth/) 做鉴权；业务域只依赖 `common`，结构上拿不到这条链。

### 模块边界（谁持什么）

| 内容 | 归属 |
|---|---|
| 店主账号 `store_user`（注册/登录/登出/me，JWT+Redis，`type=store`，**无 RBAC**） | **store-bff（本模块）** |
| 店铺 `store_shop` + 审核状态机（详情 / 保存 / 提交） | store 域（内部 Feign） |
| 店铺在售商品 `store_goods_spu`/`store_goods_sku`（CRUD + SKU 替换 + 上下架 + 库存） | store 域（内部 Feign） |
| 分类树 / 品牌列表 / 按 SKU 编码反查中台模板 | goods-center（内部 Feign） |
| 中台版本比对与「同步」提示（`centerOutdated`/`centerMissing`/`centerSpu`） | **store-bff（本模块编排）**，纯域不调中台 |
| 分类**全路径**（`categoryPath`）的读时解析 | **store-bff（本模块编排）**，域不持分类表 |
| 「店铺已审核通过」门禁 | **store-bff（本模块编排）**，域不查店铺状态 |
| 本店订单（分页 / 详情 / 发货） | trade 域（内部 Feign）。⚠ **发货是本层唯一的订单写动作**，分页 / 详情是只读 |
| 本店评价（分页 / 商家回复）与评分冗余列 | store 域（内部 Feign，`store_goods_evaluation` + 商品 / 店铺两表的 `score` 列）。⚠ 评价人的**昵称 / 头像**在 customer-center、域侧只给 `customerId`｜**商品名兜底文案**在本层 |
| 平台店铺管理（page/detail/audit）与「店铺商品管理」（page/detail/lock/unlock） | admin BFF → store 域（**与本模块无关**，同一条域接口、不传作用域） |

> 📋 对外接口清单见 [`docs/contracts/store-bff.md`](../../docs/contracts/store-bff.md)（条数与落地状态以该表为准，本 README 不另记）。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与 store 域同库，**只分表所有权**）。

| 表 | 说明 |
|---|---|
| `store_user` | 店主账号（username / password BCrypt / nickname / phone / status）；**id 即其店铺主键**（账号店同 ID） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。⚠ 建库只有一个入口：审计列 `create_user/update_user` 改 `VARCHAR(32)` 等历次结构变更的**最终形状**都已写进该文件，不再保留中间迁移脚本。

实体沿用 common `BaseEntity`（逻辑删除 + 审计字段自动填充，取值格式见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 8 条）。

> ⚠ **「账号店同 ID」**：当前店主**账号 id 即 store_id**，店铺与商品接口均以账号 id 作作用域（`loginUser.getId()`）
> **无条件覆盖**写进域入参 DTO（页面传了也不采用，见 `docs/contracts/store-bff.md`），无需按账号反查我的店。

## 三、职责与边界

### 1. 登录与登录态

- 店主登录/注册走白名单（网关 `/store/auth/login,register` + 服务侧 `/auth/login,/auth/register`，**两处都要登记**）
- 签发 `type=store` 的 JWT 并写 Redis 店主登录上下文（键格式与跨端隔离见 [common-auth/README.md](../common-auth/README.md)）
- 本地认证链：JWT / 网关注入的 `X-User-Id` + `X-User-Type` → Redis 按 `store:{id}` 重建登录店主
- **店主端不接 RBAC**：登录后对自己店全权限，因此本层**没有一个 `@PreAuthorize`**（这是预期状态，不是漏登记）

### 2. 业务门禁（不在域内，全在本层）

四条门禁的规则本体全在本层，域侧都不做：

- **店铺已审核通过**：`/goods/**`（**含读接口**）先经 `StoreClient.getShop(storeId)` 判 `status == 2`（**返 null 即未开店**，不是故障、不得降级成「暂不可用」），未过审回 `code=403`。**域不查店铺状态**
- **中台版本同步**：详情调 `GoodsCenterClient.spuDetail()` 比对中台 `version` 与落库 `center_version`，不等则给 `centerOutdated` + `centerSpu`（**覆盖与否由店主决定，不阻断保存**），中台已删/不可达 → `centerMissing`（**不报错**）。**域不调中台、不判版本**
- **分类全路径**：`categoryPath` 读时调 `GoodsCenterClient.categoryPaths(ids)` 批量补全（页内去重、非 N+1）；**失败只告警、路径留空**，前端回退快照名——展示增强不得拖垮主流程。**域不持分类表**
- **锁定商品只读**：锁定商品在店主端整行只读，且**不展示锁定人**（仅平台端展示）。域内强制，本层透出

### 3. 编排与降级

- `StoreShopBffService` / `StoreGoodsBffService` / `StoreOrderBffService` / `StoreEvaluationBffService`（`com.panoramic.storebff.bff`）**只做编排**，不持域实体：分别持 `StoreClient`（商品那个另持 `GoodsCenterClient`，订单那个持 `TradeCenterClient`，评价那个另持 `CustomerCenterClient`）。
- **业务异常原样透传，故障才降级**：下游业务异常（400 参数/业务，如「审核中锁定」「提交前请补全」；403；404）原样透传给页面，由 common 统一异常处理还原 `RespData`；**熔断/连接/序列化**等降级为「店铺服务暂不可用，请稍后重试」。
- 该「剥 cause 链还原业务异常 / 其余降级」的逻辑抽在 common 的 **`com.panoramic.common.feign.BffFeignCall`**，store-bff 与 admin BFF 共用一份，各端只传自己的降级文案。
- 熔断参数见 Nacos 共享配置 `feign-circuitbreaker.yml`（与 admin 同源一份），降级口径见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 13 条。
- 出站**只**原样透传 `X-User-Id`/`X-User-Type`（**不做 goods 版「缺省回退 admin」的写死兜底**，避免店主侧被盖成 admin）；**不带任何内部令牌**（该信任头已删除——store 域不鉴权）。

### 4. 评价页（商户端）

**独立菜单页「评价」**（与「订单管理」平级），**不是**订单详情里的子区块。

- **与 C 端刻意不对称**：商户端**要星级筛选、不要星级分布**（C 端要分布、不要筛选）——分布是展示、筛选是操作，商户按星级翻差评才是刚需。故本层页面入参有 `score`（**单选**），出参**没有**分布字段
- **单选 → 集合的转换在本层**：页面按「全部 / 5 星 / …」单选，域侧入参是集合 `scores`（非空即 `IN` 过滤），本层转成单元素集合。⚠ 本接口是 `GET`，集合塞进 query string 会被 axios 序列化成 `scores[]=5` 形状——将来页面改多选时只把页面 DTO 换成 `List<Integer>`，**域侧不动**
- **商品筛选**复用既有在售商品分页（`GET /goods/spu/page`），不为它新造一套；**每行显示商品名**由 store 域按 `spuId` 批量反查，
  商品已软删 → 域侧下发 `null`、**本层填兜底文案「商品已删除」**（不报错、不整页失败）
- **评价人昵称 / 头像经 customer-center**：域出参只有 `customerId`（内部 id，**不下发**），本层一页**一次**批量取
  （**禁止**逐条 `getProfile`：一页 10 条就是 10 次跨服务往返）。⚠ 这条是**读增强、静默降级**：取不到按占位「用户」+ 头像 `null` 下发，
  **不拖垮评价列表**（故它**不**走 `BffFeignCall`——那语义是降级成 500 文案）。⚠ **读取侧不拼手机号后 4 位**（那需要手机号，商户端拿不到）：
  该规则只在 mall-bff 的**写入侧**实现一处
- **回复**：一条评价至多一个回复，**回复后不可改、不可删**（需求未提，属边界），评价人也不能再回；回复**不影响评分**、不写状态轨迹。
  判据在域侧（以「回复列为空」为条件更新、影响行数是唯一依据），本层**不重判**：重复回复的域侧 `400`「该评价已回复」原样透传
- ⚠ 评价文字与回复文字按**纯文本插值**渲染：本层**不接 `HtmlSanitizer`**（那条口径的前提是「店主自由录入 HTML」），前端**不许 `v-html`**

### 5. 边界（本层不做什么）

- **不持域实体、不落域表**：本层只有 `store_user` 一张表，店铺与商品数据全在 store 域
- **不做数据归属判断**：按作用域过滤由 store 域按 `store_id` 列做，本层只负责从登录态取出 store_id 无条件写进域入参
- **不直接改上下架推导量**：`shelf_status` 是域内 `refreshShelfStatus` 的推导结果，本层只提交 SKU 上下架意图
- **评价只编排、不自持**：评价数据（含商品 / 店铺的评分冗余列）全在 store 域 `store_goods_evaluation`；
  本层补的只有那两件域做不到的事——评价人昵称头像（**跨 customer-center**，域侧只持 `customerId`）与商品名兜底文案。
  ⚠ **评分是域内推导量**（提交 / 回复时域侧重算），本层**不写也不重算**

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供，默认指向 `123.56.117.17:3306`（库 `panoramic_mall`）；连接其他库请注入环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见该共享配置，账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：`datasource-mysql.yml` / `datasource-redis.yml` / `auth.yml` / `feign-circuitbreaker.yml`（`jwt-secret` / `redis-prefix` / `header-name` 由端 BFF 与 gateway 同源；熔断参数与 admin 同源一份）。import **不带 `optional:`**——缺任一则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
- `@EnableFeignClients(basePackages = {"com.panoramic.contract.store", "com.panoramic.contract.goods", "com.panoramic.contract.trade", "com.panoramic.contract.customer"})` 扫描内部 Feign 客户端（store 域 + goods-center + trade-center + customer-center）
- 响应结构：成功 `code=200`；业务校验失败 `code=400` 携带中文提示；店铺未过审 `code=403`；下游不可用统一 `code=500`（store 域「店铺服务暂不可用」/ 中台「商品服务暂不可用」/ 评价「评价服务暂不可用」/ 订单「订单服务暂不可用」）
