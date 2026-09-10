# store — 店铺业务域（下沉纯域）

全景商城**店铺业务域**（Servlet 技术栈，由原 `store-center` 更名拆分而来），端口 **8083**。
本切片（2026-09-07）把原 store-center 拆为 **store（本模块，下沉纯域）** + **store-bff（店铺端 BFF）**：
- **store 域持 `store_shop`**（店铺资料 + 审核状态机）与 **`store_goods_spu`/`store_goods_sku`**（店铺在售商品，2026-09-10 新增），不带店主登录/页面编排；
- 店主账号 `store_user` 归 **store-bff**（见 [store-bff/README.md](../store-bff/README.md)）；
- 页面请求**不再直连本域**：店主端接口经 store-bff、admin 店铺管理经 admin BFF 编排，本域只被各端 BFF 经注册中心内部 Feign（`/internal/store/**`）调用，**不开放公网路由**。

## 数据模型与核心决策

- **账号店同 ID（一人一店）**：`store_shop.id == 店主账号 id`（`IdType.INPUT`，建店时由 store-bff 带入账号 id）；已删除 `owner_user_id` 列与 `uk_owner_user_id`，天然一人一店。
- **store_id 通用数据权限（D5）**：owner 侧方法必带 `store_id`、只作用于「store_id == 传入值」的行；platform 侧方法不带 `store_id`、全量。
  - 在售商品（`store_goods_*`）**owner 侧以「id + store_id」双条件取行**（`StoreGoodsSpuServiceImpl#getOwnedOrThrow`）：他人商品与不存在的商品同样报「商品不存在」，不泄露存在性；SKU 不持 `store_id`，先校验其 SPU 归属再操作。
  - **owner/platform 的分流由「哪个 BFF 调哪一侧接口」决定，域内不做身份断言**（原 `assertOwner`/`requirePlatformAdmin` 已删）：store-bff 调 owner 侧并从登录态取 store_id，admin 调 platform 侧并由 `@PreAuthorize` 把关。`audit_by` 直取 `X-User-Id` 仅留痕（不与平台账号联查，**D6：admin 不读店主账号**）。
- **域内不做审核门禁**：店铺 `status == 2`（已通过）的判断由端 BFF 编排时前置。

## 功能模块（审核状态机）

审核状态：**0草稿 → 1待审核 → 2已通过 / 3已驳回**（店主可编辑重提）。

| 状态 | 保存草稿(owner save) | 提交(owner submit) | 平台审核(platform audit) |
|---|---|---|---|
| 无店铺(id=store_id 无行) | 建草稿(0) | 提交(1) | — |
| 0 草稿 | 更新草稿(0) | 提交(1) | — |
| 1 待审核 | ❌ 审核中锁定 | ❌ 重复提交 | ✅ 通过(2)/驳回(3) |
| 2 已通过 | ❌ 信息锁定只读 | ❌ 无需提交 | — |
| 3 已驳回 | 回到草稿(0) 清留痕 | 重新提交(1) | — |

- **提交即校验完整资质**：联系人/电话/省市区+详细地址/营业执照名称/统一社会信用代码/执照照 均必填；保存草稿不强制
- **审核只对「待审核(1)」做条件更新**（`update ... where status=1`）：并发/重复审核时更新 0 行即拒绝，防重复审核
- 驳回必须填原因（`audit_remark`）；审核人/审核时间（`audit_by/audit_time`）仅留痕记录，不与平台用户表联查
- 编辑驳回回草稿/驳回重提时把提交/审核留痕列显式写 NULL（MP update 默认跳过 null 列，实体置空不够）

## 功能模块（店铺在售商品，2026-09-10 新增）

字段与中台标准商品同构，SKU 额外带 `price`（**不建库存列**）；分类/品牌存「id 引用 + 名称快照」，保存时不回查中台。
上下架规则收敛为一句话：**SPU 上架 ⟺ 至少一个 SKU 上架**（不变量，无独立 SPU 上下架入口）。

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
| R11 | 全部方法为 owner 侧，入口以「id + store_id」限定作用域 |

## 数据库（库：`panoramic_mall`）

| 表 | 归属 | 说明 |
|---|---|---|
| `store_shop` | store（本域） | 店铺（主键=店主账号 id + 资质字段 + 审核状态/留痕字段） |
| `store_goods_spu` | store（本域） | 店铺在售商品 SPU（含中台关联 `goods_spu_id` + 版本戳快照 `center_version` + `shelf_status`） |
| `store_goods_sku` | store（本域） | 店铺在售商品 SKU（规格组合 + 编码 + 图片 + `price`；无库存列） |
| `store_user` | store-bff | 店主账号（见 store-bff schema） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）；审计列改造见 `db/migrate-audit-usertype.sql`（2026-09-10）。字段沿用 common `BaseEntity` 约定：逻辑删除 + 创建/更新时间与操作人（MP 自动填充）——操作人 `create_user`/`update_user` 为 **`VARCHAR(32)`**，值为 **`UserType:UserId`**（如 `store:5`）；`audit_by` 是审核人留痕列（平台管理员 id），维持 `BIGINT UNSIGNED` 不变。

## 接口清单（内部，前缀 `/internal/store`，Feign 方法直返业务类型不包 RespData）

### owner（store-bff 调用，必带 store_id；只作用于 id==store_id 的店）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/store/shops/mine?storeId=` | 我的店铺；**无店返回 200 + JSON null**（Feign 解出 null，勿改抛异常） |
| POST | `/internal/store/shops/{storeId}/save` | 保存草稿（无店则建 id=storeId） |
| POST | `/internal/store/shops/{storeId}/submit` | 提交审核（完整资质校验→待审核） |

### platform（admin 调用，不传 store_id 全量；由 admin `@PreAuthorize` 把关）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/store/shops/page` | 分页（pageNum/pageSize/status/keyword）→ `PageResult<ShopVO>` |
| GET | `/internal/store/shops/{id}` | 详情（无店主账号信息） |
| POST | `/internal/store/shops/{id}/audit` | 审核 `{approved, auditRemark}`（驳回原因必填） |

### owner：店铺在售商品（store-bff 调用，必带 store_id；本组**无 platform 对应物**）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/internal/store/goods/spu/page?storeId=&pageNum=&pageSize=` | 我的商品分页（keyword/categoryId/brandId/shelfStatus 可选）→ `PageResult<StoreGoodsSpuPageItemVO>` |
| GET | `/internal/store/goods/spu/{id}?storeId=` | 商品详情（含 SKU 列表）→ `StoreGoodsSpuDetailVO` |
| POST | `/internal/store/goods/spu?storeId=` | 新增商品（可一并落 SKU）→ 新商品 id |
| PUT | `/internal/store/goods/spu/{id}?storeId=` | 修改商品（基础信息 + 规格配置；存在上架 SKU 时规格配置只读） |
| DELETE | `/internal/store/goods/spu/{id}?storeId=` | 删除商品（存在上架 SKU 时拒绝；否则级联软删 SKU） |
| PUT | `/internal/store/goods/spu/{id}/skus?storeId=` | SKU 整单替换（未上架可增/改/删；已上架须原样保留） |
| PUT | `/internal/store/goods/spu/{spuId}/skus/{skuId}/shelf?storeId=` | SKU 上下架 `{shelfStatus}`（反向联动 SPU） |

> `StoreGoodsSpuDetailVO` **只含店铺侧字段**：中台版本比对结果（是否过期/中台模板快照）由 store-bff 编排时补充
> （纯域不调中台，`center_version` 仅作快照落库）。DTO/VO 上移 common（`com.panoramic.common.store.dto/vo`），与接口同源。

> admin 侧页面接口经 admin BFF 的 `/admin/shop/shops/**`（`@PreAuthorize store:shop:list/audit`）；店主侧页面接口经 store-bff 的 `/store/shops/**`。`StoreClient` 与 DTO/VO（`ShopSaveDTO/ShopAuditDTO/ShopPageQueryDTO/ShopVO/PageResult`）上移 common（`com.panoramic.common.store`），与接口同源，勿在端 BFF 复制。

## 信任与防线

- 本域不启用 Feign 客户端扫描（纯被调方）。
- **本域不做鉴权、不做权限判断**（2026-09-10 起）：入口只有两道——`StoreUserIdentityFilter`（把透传的 `X-User-Id`/`X-User-Type` 直取填 `UserContext`，供审计填充与 `audit_by` 留痕；**缺头即不填充、放行**，不回 401）→ 本地 `StoreSecurityConfig`（唯一一条全放行链，避免 Spring Security 默认链拦截 actuator）。原 `InternalTrustFilter`（验 `X-Internal-Token`）与 `assertOwner`/`requirePlatformAdmin`（域内 userType 断言）**均已删除**。
- owner/platform 的分流**由「哪个 BFF 调哪一侧接口」决定**：store-bff 从登录态取账号 id 作 store_id 调 owner 侧，admin 调 platform 侧并由 `@PreAuthorize` 把关。⚠ 前提是 `8083` 端口只在内网可达，防线在网络层。
- `application.yml` 不声明 auth 白名单；**不引入** `datasource-redis.yml` / `auth.yml`（本域不依赖 `common-auth`，结构上拿不到认证链与 Redis）。

## 配置说明

- **数据源**：默认本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程/定制库请注入环境变量：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见 Nacos `datasource-mysql.yml`，账号密码勿写入代码或提交到仓库）
- MyBatis-Plus：主键 `IdType.INPUT`（store_shop.id=账号 id）、`is_delete` 逻辑删除、驼峰映射
- 启动类扫描 `com.panoramic` 以加载 common 的全局异常处理、分页插件、字段自动填充与安全链；**异常经 `StoreDomainExceptionHandler` 还原真实 HTTP 状态 + `{code,msg}`**（供内部 Feign ErrorDecoder 还原）
- 响应结构：对外统一 `RespData`（端 BFF 侧）；本域内部接口**不包 RespData**
