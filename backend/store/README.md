# store — 店铺业务域（下沉纯域）

全景商城**店铺业务域**（Servlet 技术栈，2026-09-07 由原 `store-center` 拆分而来），端口 **8083**。

本域持**店铺资料 `store_shop`（含审核状态机）**、**店铺在售商品 `store_goods_spu` / `store_goods_sku` / `store_goods_sku_stock` / `store_goods_sku_stock_log`** 与**商品评价 `store_goods_evaluation`（含评分统计）**，不带店主登录、不带页面编排。

## 一、架构位置

**下沉纯域（第 ② 层）**：不向页面暴露公网路由，只被各端 BFF 经注册中心**内部 Feign** 调用（另有一条域间调用边：trade-center 的订单流水线，见下表与第 24 条）。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | store-bff（**带作用域**：我的店铺、店铺商品、库存——`storeId` 取自店主登录态） | `store-interface` 的 `StoreClient`，带熔断降级 |
| 被谁调 | admin BFF（**不带作用域**：店铺管理审核、店铺商品跨店管理与锁定） | 同上 |
| 被谁调 | mall-bff（**不带作用域**：C 端商品分页、筛选聚合与详情） | 同上 |
| 被谁调 | **trade-center（域，不是端）**：下单快照 + 库存扣减 / 回补（无锚点，按资源 id 操作） | 同上（`/goods/trade/**`）——⚠ 全仓**唯一的跨域调用边**，见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 24 条 |
| 本域调谁 | — | **不启用 Feign 客户端，纯被调方** |

- 店主账号 `store_user` 归 **store-bff**（见 [`../store-bff/README.md`](../store-bff/README.md)）；本域**不持店主账号**，平台侧也不与店主账号联查（D6）
- 本域只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链与 Redis；⚠ 域端口只在内网可达是**安全前提**，本域不做鉴权，防线在网络层、不在应用层

> 📋 对外接口清单（按**有无作用域维度**分组，不分端）见 [`docs/contracts/store.md`](../../docs/contracts/store.md)（条数与落地状态以该表为准，本 README 不另记）。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 归属 | 说明 |
|---|---|---|
| `store_shop` | **store（本域）** | 店铺（主键 = 店主账号 id + 资质字段 + 审核状态/留痕字段 + 评分冗余列 `score`） |
| `store_goods_spu` | **store（本域）** | 店铺在售商品 SPU（中台关联 `goods_spu_id` + 版本戳快照 `center_version` + `shelf_status` + `min_price` + 评分冗余列 `score` + 平台锁定 `lock_status/lock_reason/lock_user/lock_time`） |
| `store_goods_sku` | **store（本域）** | 店铺在售商品 SKU（规格组合 + 编码 + 图片 + `price`；**无库存列**） |
| `store_goods_sku_stock` | **store（本域）** | SKU 库存（`stock` / `warn_stock`；`locked_stock` **已废弃并删列**（2026-09-21 废弃、2026-09-22 删列，不参与口径、不再写入）；与 `store_goods_sku` 1:1、**独立成表**，使库存写锁不落 SKU / SPU 行）；归属链 `sku_id → sku.spu_id → spu.store_id`，不冗余 `store_id` / `spu_id` |
| `store_goods_sku_stock_log` | **store（本域）** | SKU 库存变动流水（`sku_id` / `order_no` / `kind` = `OUT` 出库 · `REVERT` 回补 / `change_quantity` 恒正 / `occurred_at`）；**只增不改**（不提供 update / delete 入口）；唯一键 `(order_no, sku_id, kind)` 是回补幂等的落库兜底 |
| `store_goods_evaluation` | **store（本域）** | 商品评价（`order_no + spu_id` 唯一 + 评价人 `customer_id` + `score` 1~5 + 文字 + `sku_snapshot` JSON + 商家回复 `reply_content`/`reply_time`；`store_id` 是冗余列）；**只增**（无修改/删除入口），**也是两处评分冗余列的唯一来源** |
| `store_user` | store-bff | 店主账号（见 store-bff schema，**不在本域**） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行；含为存量库补列（锁定 / 最低价 / 两处评分）的幂等守卫块）。⚠ 建库只有一个入口：历次结构变更的**最终形状**都已写进该文件，不再保留中间迁移脚本。

实体沿用 common `BaseEntity`（逻辑删除 + 审计字段自动填充，取值格式见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 8 条）。⚠ 例外：`audit_by` 是**审核人留痕列**（平台管理员 id），维持 `BIGINT UNSIGNED` 不变。

## 三、职责与边界

### 1. 数据权限模型

- **账号店同 ID（一人一店）**：`store_shop.id == 店主账号 id`（`IdType.INPUT`，建店时由 store-bff 带入账号 id）；已删除 `owner_user_id` 列与 `uk_owner_user_id`，天然一人一店。
- **store_id 通用数据权限（D5）**：作用域是**入参 DTO 里的一个字段**（§22/§23）——**传了就只作用于「store_id == 传入值」的行，没传就是不限定**（域内不判身份、不按端分流；原 `assertOwner` / `requirePlatformAdmin` 已删）。
  - **同一个能力只有一条路径**：端别差异只在「传不传作用域」，值**只能取自调用方登录态**（`LoginUser.getId()`），域侧只管「传了就筛」。
  - 在售商品（`store_goods_*`）传作用域时以「id + store_id」双条件取行（`StoreGoodsSpuServiceImpl#getOwnedOrThrow`）：他人商品与不存在的商品**同样报「商品不存在」**，不泄露存在性；SKU 不持 `store_id`，先校验其 SPU 归属再操作。
  - **新增方法按什么决定作用域字段**：看**作用对象表是否带 `store_id` 列** + 该能力**是否存在合法全量视角**——只能限定「本店」的（店铺保存、在售商品写、库存、评价回复）作用域**必填**（`@NotNull(groups = StoreScopeGroup.class)` + 域入口 `@Validated({Default.class, StoreScopeGroup.class})`，缺了即 HTTP 400）；存在全量视角的（详情、跨店分页、锁定、评价三条读）作用域**可空或没有**，由各调用方自设。⚠ 评价提交是**第四类**：锚点是**评价人 `customerId`** 而非店铺（店铺归属域内按 `spuId` 反查），见 §5。
  - `audit_by` 直取 `X-User-Id` 仅留痕（不与平台账号联查，D6）。

### 2. 店铺审核状态机

审核状态：**0 草稿 → 1 待审核 → 2 已通过 / 3 已驳回**（店主可编辑重提）。

| 状态 | 保存草稿(save，带作用域) | 提交(submit，带作用域) | 平台审核(audit，无作用域) |
|---|---|---|---|
| 无店铺(id=store_id 无行) | 建草稿(0) | 提交(1) | — |
| 0 草稿 | 更新草稿(0) | 提交(1) | — |
| 1 待审核 | ❌ 审核中锁定 | ❌ 重复提交 | ✅ 通过(2)/驳回(3) |
| 2 已通过 | ❌ 信息锁定只读 | ❌ 无需提交 | — |
| 3 已驳回 | 回到草稿(0) 清留痕 | 重新提交(1) | — |

- **提交即校验完整资质**（联系人/电话/省市区+详细地址/营业执照名称/统一社会信用代码/执照照均必填），保存草稿不强制
- **审核只对「待审核(1)」做条件更新**（`update ... where status=1`）：并发/重复审核时更新 0 行即拒绝
- 驳回必须填原因（`audit_remark`）；审核人/时间（`audit_by`/`audit_time`）仅留痕，不与平台用户表联查
- 编辑驳回回草稿/驳回重提时把提交/审核留痕列**显式写 NULL**（MP update 默认跳过 null 列，实体置空不够）
- ⚠ **审核门禁不在域内做**：店铺 `status == 2`（已通过）的判断由端 BFF 编排时前置

### 3. 店铺在售商品

字段与中台标准商品同构，SKU 额外带 `price`；分类/品牌存「id 引用 + 名称快照」，保存时**不回查中台**；分类**全路径**域内不解析（域不持分类表），由端 BFF 读时调 goods-center 补全。

上下架规则收敛为一句话：**SPU 上架 ⟺ 至少一个 SKU 上架**（不变量，无独立 SPU 上下架入口）。

规则：

- **新增**（R1）：SPU 与全部 SKU 一律下架态落库，每个 SKU `price` 必填且 ≥0.01；**未上架 SKU 可增、可改、可删**（R5）
- **上下架是推导结果**（R2/R3）：上架任一 SKU → SPU 自动上架；SKU 全下架 → SPU 自动下架（`refreshDerived` → `refreshShelfStatus`，不手写）
- **已上架 SKU 整行锁死**（R4）：规格组合/价格/编码/图片不可改、不可删（整单替换时缺行即拒绝），须先下架；连带**存在上架 SKU 时** `spec_config` 只读（R6，防 SKU 组合孤儿）、SPU 不可删（R8）
- **可自由改**（R7）：名称/主图/轮播图/详情/分类/品牌任何时候都可改；删 SPU（R8）在无上架 SKU 时软删并**级联软删**其下全部 SKU
- **最低价推导**（R13）：`min_price` = 名下**上架且未删** SKU 的最低价，SKU 全下架时清空为 NULL（`refreshDerived` → `refreshMinPrice`，不手写）
- **不在域内的三条**（R9–R11）：审核门禁（`status == 2`）与中台版本同步由端 BFF 编排，店主视角的作用域由「id + store_id」限定（见上「数据权限模型」）
- **R12** 平台锁定见下「4. 平台锁定规则」；**R14** 库存锁隔离见下「库存口径」

> ⚠ `refreshDerived` 是**推导量统一刷新入口**，内含两个不变量写者：`refreshShelfStatus`（「SPU 上架 ⟺ ≥1 SKU 上架」）与
> `refreshMinPrice`（「`min_price` = 名下上架未删 SKU 最低价」）。任何时候都不要绕过它直接改 `shelf_status` / `min_price`——包括锁定时的级联下架。
> ⚠ 两者**各自独立比较**（下架高价 SKU 后上下架不变、最低价却变了），且 `min_price` 可被清成 NULL，
> 回写只能用 `lambdaUpdate().set(...)`：`updateById` 跳过 null 列，会把「SKU 全下架 → 清空 `min_price`」静默丢掉。

**库存口径（R14）**：

- **可用库存 = `stock`**（`locked_stock` 已于 2026-09-21 废弃，不再参与口径），**C 端展示的一律是可用库存**；`warn_stock` 仅商户端低库存预警用（NULL = 不预警），**不进 C 端**
- **库存不参与**「SPU 上架 ⟺ ≥1 SKU 上架」**不变量**（归零不触发任何下架），**也不参与 C 端可见性**（售罄商品照常可打开，C 端标售罄）
- **平台锁定期库存同样只读**（店主视角整行只读由 `assertNotLocked` 域内强制）
- **库存独立写操作不加外层 `@Transactional`**（单条语句自带事务，不拉长持锁时间）；**例外两处**：`replaceSkus` 内「新建 SKU + 建库存行」同一事务（只锁新建行），以及交易协作的扣减 / 回补（库存变更 + 流水写入必须**同成同败**）
- 写入点七处：库存页单行改、库存页批量改、新建 SKU 带初始库存、SKU 删除级联删、SPU 删除级联删、**交易协作·下单扣减**、**交易协作·按单回补**；前五处的**归属校验**（`sku_id → sku.spu_id → spu.store_id`）与「仅新建行采信初始库存」的判定都在带作用域的商品编排里做，库存 service 不认识 `store_id`
- **交易协作的扣减 / 回补（R18/R19，域间调用）**：⚠ 这两条**没有 `store_id` 锚点**（调用方是 trade-center，手上只有 `skuId` 与订单号），故**不做归属校验、不判锁定、不判上下架**——可见性由交易侧的商品校验判，本域只管库存数够不够
  - **扣减**：库存表上一条**原子条件更新**（`stock = stock - ? where sku_id = ? and stock >= ?`），**影响行数是唯一判据**；够则记一条 `OUT` 流水并返回 `true`，不够则返回 `false` 且**不记流水**——⚠ **库存不足不是 HTTP 错误**（R18），由交易域翻成业务错误
  - **回补**：取该单全部 `OUT` 流水，对**尚无 `REVERT` 流水**的每条做**无守卫**回补（`stock = stock + ?`）并记一条 `REVERT`；已有 `REVERT` 的跳过 → **重复调用是 no-op**；⚠ **补偿路径宁可不做也不能炸**（R19）——单号为空、该单没扣过、库存行已不在，一律静默 no-op，**绝不抛异常**（它在失败回滚链路上跑）
  - 流水**只增不改**：写入口径是 `StoreGoodsSkuStockLogService`（唯一 owner，库存 service 不持流水 Mapper）

### 4. 平台锁定规则（R12）

`store_goods_spu` 的 `lock_status` / `lock_reason` / `lock_user` / `lock_time` 四列即锁定态（**不建独立锁定表**）：

- **锁定写入**：`lock_status=1` + `lock_reason`（必填）+ `lock_user` + `lock_time`；已锁定则拒绝重复操作（**条件更新 `where lock_status=0`**，防并发）
- **级联下架**：锁定时把名下**已上架** SKU 批量置下架，再由 `refreshDerived` 重推 SPU 为下架（`refreshShelfStatus` 与 `refreshMinPrice` 一并重算）——**不变量不变、仍是唯一入口**，不绕过它直接改 `shelf_status` / `min_price`
- **锁定期店主视角整行只读**：编辑 / 删除 / SKU 整单替换 / SKU 上下架 / 改库存一律拒绝（`assertNotLocked` 域内强制，不只靠前端禁用按钮）
- **解锁**：清空 `lock_reason`/`lock_user`/`lock_time`、`lock_status=0`（**必须 `lambdaUpdate().set(col, null)` 显式清**，`updateById` 跳过 null 会清不掉）；**不动 SKU 与 `shelf_status`**——保持下架，由店主手动重新上架
- **锁定人**：`UserContext` 直取，按审计同格式存 `UserType:UserId`。这是**业务列而非审计列**（D7），故在 service 内显式写入；**店铺端不展示锁定人**，仅管理端展示
- **权限**：平台侧由 admin BFF 的 `@PreAuthorize store:goods:lock` 把关；**域内不做任何权限判断**

### 5. 商品评价与评分（R15–R17）

评价挂在**商品 SPU** 上：**一笔订单里的一个商品一条评价**（唯一键 `(order_no, spu_id)`）。同一订单里同一 SPU 下的多个 SKU **合成一条**——因为顾客对「这件商品」的整体体验打分，不是对每个规格打分；下单时的 SKU 行组存进 `sku_snapshot`（规格 / 单价 / 数量），属**历史事实**，商品改价、SKU 被删都不改写它。

- **R15 写入与唯一性**：`submitEvaluation` 的入参是「订单号 + 商品 + 评价人 + 1~5 星 + 文字 + 快照」；**店铺归属由域内按 `spuId` 反查**（调用方传不了，故不存在「store_id 与 spu_id 不一致」这种输入）。重复提交同单同商品 → **400「该商品已评价」**（先查唯一键，再以撞键翻译兜住并发窗口，不静默改写）；**商品软删/下架/锁定后历史订单照常可评价**（反查刻意绕过逻辑删除）。
- **R16 评分口径**（`score` 两列的唯一写入口是 `StoreGoodsEvaluationServiceImpl#refreshScores`）：**商品评分** = 该 SPU 全部评价的算术平均；**店铺评分** = 该店全部评价的算术平均（每笔等权，不按商品加权）；均保留 1 位小数，**无评价 = NULL**（不是 0 分，前端渲染「暂无评分」）。写入评价时在**同一事务内**重算两处并回写（**重算而非增量累加**）；两个回写都走 `lambdaUpdate().set(...)`（`updateById` 跳过 null 列，清不回 NULL）。⚠ 它**不在 `refreshDerived` 里**——触发源是评价变动而不是 SKU 变动；店铺自身的三个写路径（保存草稿 / 提交 / 审核）都不得显式设置该列。
- **R17 商家回复**：**一条评价至多一条回复**（`reply_content` + `reply_time` 就在评价行上，**不另立回复表**，也没有「改回复 / 删回复」入口）；幂等由**条件更新（`reply_content is null`）+ 影响行数**承担，不是先读后写（先读只为把「已回复」与「评价不存在」分成两种话术）。归属校验按「id + storeId」双条件，**他人评价与不存在的评价同样报「评价不存在」**，不泄露存在性。**回复不改评分**（评分只由星级决定）。
- **不在域内的两条门禁**：① **「只有已完成订单能评」**由 **mall-bff 前置业务校验**（域不持订单，判状态就要新增 `store → trade` 的域间边，违反 cross-cutting 第 24 条）；② 审核状态与「评价人是不是本人」同样由端 BFF 收口。域侧只守自己的不变量（同单同商品唯一 + 星级 1~5）。
- **读能力跨店通用**：分页（时间倒序，`create_time` + `id` 双键保证全序）与星级分布（**固定 1~5 五行、缺的补 0**，避免前端按「实际出现的星级」画柱而错位）都按 `spuId` / `storeId` 可选筛选；分页出参带 `customerId` 与 `spuName`（**商品已软删时为 null**，兜底文案由端 BFF 定），昵称头像由端 BFF 批量补（域不持顾客资料）。

### 6. 边界（本域不做什么）

- **不做任何鉴权、不做任何权限判断、不校验 token**；唯一授权点是调用方端 BFF 的 `@PreAuthorize`（交易协作那三条的调用方是 **trade-center 这条域间边**，同样不鉴权、无 `@PreAuthorize` 可挂——防线仍是「域端口只在内网可达」）；**不装配认证链**（不打 Redis、不查登录态，`application.yml` 不声明 auth 白名单）
- **不做审核门禁、不做版本比对、不解析分类路径**——三者都是调用方 BFF 的编排职责
- **不持店主账号、不持分类表**
- **跨店通用侧（`/goods/cross-shop/spu/page` 与 `/goods/facets`）无数据权限锚点**：限定条件（含 `shopStatus` / `shelfStatus` / `lockStatus`）**全由调用方自设**，域内不判身份、不做端别分流、**不含任何 C 端隐含约束**（C 端固定三个条件的口径在 mall-bff）；两者走 `POST + @RequestBody`（入参含集合，规避 `@SpringQueryMap` 序列化口径问题）。见 [cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 17–19 条

### 7. 信任与防线

入口只有两道：

1. `StoreUserIdentityFilter` —— 把透传的 `X-User-Id`/`X-User-Type` 直取填 `UserContext`，供审计填充与 `audit_by` 留痕；**缺头即不填充、放行**，不回 401
2. 本地 `StoreSecurityConfig` —— 唯一一条全放行链，避免 Spring Security 默认链拦截 actuator

原 `InternalTrustFilter`（验 `X-Internal-Token`）与 `assertOwner` / `requirePlatformAdmin`（域内 userType 断言）**均已删除**。

> ⚠ 本域**不做鉴权是有意设计，不是疏漏**。安全性完全依赖 `8083` 端口只在内网可达。

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供（默认指向 `123.56.117.17:3306`，库 `panoramic_mall`）；连接其他库请注入环境变量 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置加载**：只引入 `datasource-mysql.yml`，且 import **不带 `optional:`**——缺该 dataId 则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
- MyBatis-Plus：主键 `IdType.INPUT`（store_shop.id = 账号 id）、`is_delete` 逻辑删除、驼峰映射
- 启动类扫描 `com.panoramic` 以加载 common 的全局异常处理、分页插件、字段自动填充与安全链
- 异常语义（内部）：业务失败以 **HTTP 200 + `{code,msg}`** 返回（域侧不抛异常，故调用方 Feign 的 `ErrorDecoder` 不会被调用）；只有兜底异常才由 common 的 `GlobalExceptionHandler` 返 **HTTP 500** `{code:500,msg:"系统内部错误，请联系管理员"}` —— 那是熔断唯一的失败信号（[cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 13 条）
- 响应结构：**本域内部接口出参包 `RespData<T>`**（[cross-cutting.md](../../docs/contracts/cross-cutting.md) 第 2 条），与端 BFF 的对外接口同一形状；调用方解包走 `DomainResp#unwrap`
