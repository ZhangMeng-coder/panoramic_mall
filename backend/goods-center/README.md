# goods-center — 标准商品平台（商品域，下沉纯域）

全景商城**标准商品平台 / 标准商品模板库**（Servlet 技术栈），端口 **8081**，实现分类 / 品牌 / 标准 SPU-SKU 模板的基础数据管理（模板无价格/库存，语义为「标准商品模板」，区别于 store 域「店铺在售商品」）。

> ⚠ 本服务为**下沉纯域**：不再向页面暴露公网路由，只被各端 BFF 经注册中心**内部 Feign**（`/internal/goods/**`）调用。
> 当前消费方：**admin**（经 `com.panoramic.common.goods.api.GoodsCenterClient`，带熔断降级）；未来 store-bff（店主建品导模板）同通道。
> 页面访问统一走 BFF 编排：`/admin/goods/**`（网关）→ admin → 内部 Feign → 本服务。

## 功能模块

### 1. 分类管理（多级树）
- 分类树最多 **3 级**，新建时按父级自动计算层级，超过 3 级拒绝
- 同级下分类名不可重复（含更新场景）
- 树查询全量返回、按 `sort` 排序
- 删除保护：存在子分类或分类下存在商品时拒绝删除（逻辑删除）

### 2. 品牌管理
- 品牌 CRUD + 关键字分页查询（`keyword` 模糊匹配名称）
- 列表接口供商品表单下拉使用
- 删除保护：品牌下存在商品时拒绝删除

### 3. SPU/SKU 商品管理
- **SPU 管理（基础信息 + 规格属性配置）**：新增/编辑/删除/展示·隐藏、分页筛选（分类/品牌/展示状态/名称关键字）、详情（含 SKU、规格属性配置与分类完整链条）。SPU 为商城**标准商品信息模板**，无上下架概念，以 **展示/隐藏** 表示对商城是否可见；允许先保存基础信息、暂不维护 SKU（0 SKU）
  - 商品必须挂在**叶子分类**下；展示中的商品禁止删除
  - **规格属性配置**（`spec_config`）：定义各规格维度及其可选项（如 `[{"spec":"颜色","values":["黑色","白色"]}]`）；更新基础信息时不得移除仍被存量 SKU 使用的规格/属性值（防孤立守卫），SKU 的调整需走「规格」管理
- **SKU 独立管理**（规格属性组合，无价格/库存字段）：
  - 通过 `PUT /internal/goods/spu/{id}/skus` **全量替换**；空 `skus` = 清空该 SPU 全部 SKU
  - 组合必须来自该 SPU 的规格属性配置（各维度取一个值，与配置逐项匹配），否则拒绝
  - diff 策略：入参带 `id` 的 SKU 校验归属后更新、`id` 为空的新 SKU 插入、缺失的存量 SKU 逻辑删除 —— 保证已存在 SKU 的 `id` 稳定（供后续价格/库存模块引用），整体事务回滚
- 轮播图（`image_list`）、规格属性配置（`spec_config`）与 SKU 规格（`spec_attrs`）以 **JSON 列**存储，服务层 Jackson 转换
- **版本戳 `version`（`BIGINT`，Unix 毫秒）**：`goods_spu` 与 `goods_sku` 各一列，本行**任何修改即刷新**（SPU 自身字段变更、或其任一 SKU 变更，都会刷新该 SPU 的 `version`）；详情 `SpuDetailVO.version` 对外返回。用途：店铺端在售商品关联中台模板后记录该戳，编辑时比对以判断「中台模板是否已更新」，供店主选择是否同步覆盖（比对与提示在 store-bff 编排侧做，**域内不做版本判断**）。
- **按 SKU 编码反查 SPU**（`GET /internal/goods/spu/by-sku-code?skuCode=`）：供店铺端「新增商品时按中台编码整单预填」；`goods_sku.sku_code` 无唯一索引（中台不强制唯一），命中多条时按 `id asc` 取第一条并回传 `matchedSkuCount` 提示，未命中返回 `spu=null`（成功响应）。

## 数据库（库：`panoramic_mall`）

| 表 | 说明 |
|---|---|
| `goods_category` | 商品分类（多级树，parent_id 自关联） |
| `goods_brand` | 品牌 |
| `goods_spu` | 商品 SPU（含 image_list、spec_config JSON 列） |
| `goods_sku` | 商品 SKU 规格组合（含 spec_attrs JSON 列） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）；审计列改造见 `db/migrate-audit-usertype.sql`（2026-09-10）。字段沿用 common `BaseEntity` 约定：主键自增 + `is_delete` 逻辑删除 + 创建/更新时间与操作人——操作人 `create_user`/`update_user` 为 **`VARCHAR(32)`**，值为 **`UserType:UserId`**（如 `admin:1`），由 `MyMetaObjectHandler` 自动填充。

## 接口清单（下沉域内部接口）

- **基础路径 `/internal/goods`**：仅被内部 Feign 调用。**不再校验任何令牌**（内部信任头 `X-Internal-Token` 已删除，2026-09-10）：鉴权与权限判定全部收敛在端 BFF，本域只信任并执行。⚠ 前提是 `8081` 端口只在内网可达，防线在网络层。
- **入参/出参走 common 同源类型**：DTO/VO 在 `common` 的 `com.panoramic.common.goods.{dto,vo}` 维护，调用方与被调用方引用同一份，禁止各自复制。
- **返回业务原类型、不包 RespData**：方法直接返回 `Xxx` / `List<Xxx>` / `Long` / `void`；错误由 `GoodsDomainExceptionHandler` 转成**真实 HTTP 状态码** + `{code,msg}`（Feign ErrorDecoder 据此还原为 `ServiceException` 抛给调用方）。RespData 仅用于对外页面/网关接口（admin BFF 层包装）。
- **鉴权与权限判定收敛在端 BFF，域内纯执行**：goods-center 不做任何鉴权、不做任何权限判断（`DomainPerms` 已删除）；唯一授权点是调用方 admin BFF 各操作的 `@PreAuthorize`（下表“权限码”列即其校验串，与 RBAC 存库权限码一一对应）。本服务只负责执行 + 审计填充——`GoodsUserIdentityFilter` 把 BFF 透传的 `X-User-Id` / `X-User-Type` 直取填 `UserContext`（**缺头即不填充、放行**，不回 401），供 `create_user/update_user` 自动填充为 `UserType:UserId`；不打 Redis、不校验 token。

| 方法 | 路径 | 端 BFF @PreAuthorize 权限码（域不判定） | 说明 |
|---|---|---|---|
| POST | `/internal/goods/categories` | `goods:category:add` | 新建分类 → `Long` |
| GET | `/internal/goods/categories/tree` | `goods:category:list` | 全量分类树 |
| PUT | `/internal/goods/categories/{id}` | `goods:category:edit` | 修改分类（名称/排序，不支持移动父级） |
| DELETE | `/internal/goods/categories/{id}` | `goods:category:delete` | 删除（有子分类/有商品则拒绝） |
| POST | `/internal/goods/brands` | `goods:brand:add` | 新建品牌 → `Long` |
| PUT | `/internal/goods/brands/{id}` | `goods:brand:edit` | 更新品牌 |
| DELETE | `/internal/goods/brands/{id}` | `goods:brand:delete` | 删除（被商品引用则拒绝） |
| GET | `/internal/goods/brands/page` | `goods:brand:list` | 分页查询（pageNum/pageSize/keyword） |
| GET | `/internal/goods/brands/list` | `goods:brand:list` | 全量列表（表单下拉用） |
| GET | `/internal/goods/brands/{id}` | `goods:brand:list` | 品牌详情 |
| POST | `/internal/goods/spu` | `goods:spu:add` | 新建 SPU（基础信息+规格属性配置）→ `Long` |
| PUT | `/internal/goods/spu/{id}` | `goods:spu:edit` | 更新 SPU（仅基础信息+规格属性配置，防孤立守卫） |
| PUT | `/internal/goods/spu/{id}/skus` | `goods:spu:edit` | 全量替换 SKU（组合须匹配规格属性配置，空 `skus`=清空） |
| PUT | `/internal/goods/spu/{id}/status` | `goods:spu:edit` | 展示/隐藏切换 `{status: 0|1}` |
| DELETE | `/internal/goods/spu/{id}` | `goods:spu:delete` | 删除（展示中拒绝，级联逻辑删除 SKU） |
| GET | `/internal/goods/spu/page` | `goods:spu:list` | 分页查询（categoryId/brandId/status/keyword，回填分类/品牌名） |
| GET | `/internal/goods/spu/{id}` | `goods:spu:list` | SPU 详情（含 skus、规格属性配置、分类完整链条、版本戳 `version`） |
| GET | `/internal/goods/spu/by-sku-code?skuCode=` | —（store-bff 调，无权限码） | 按 SKU 编码反查 SPU 模板 → `SpuBySkuCodeVO{spu, matchedSkuCount}`（未命中 `spu=null`） |

> 页面/BFF 编排侧（admin）等价接口见 admin 模块：网关 `/admin/goods/**` → admin 的 `GoodsTemplateBffService` 编排（带熔断降级），页面不再直连本服务。
> 店铺端商品页的编排在 store-bff（网关 `/store/goods/**`）：分类树/品牌列表 + `by-sku-code` 预填 + 详情版本比对（`GoodsCenterClient`）。

## 配置说明

- **数据源**：默认本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程/定制库请注入环境变量：
  `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见 `application.yml`，账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：只引入 `datasource-mysql.yml`（业务库 + mybatis-plus）。**不引入 `datasource-redis.yml` / `auth.yml`**——本服务不依赖 `common-auth`，结构上拿不到认证链与 Redis，没有登录态可查（2026-09-10）
- **身份头**：`X-User-Id` / `X-User-Type`（网关注入 → admin BFF 经 Feign 原样透传），仅用于审计填充，不做校验
- MyBatis-Plus：主键自增、`is_delete` 逻辑删除、驼峰映射、SQL 日志打印（StdOutImpl，上线前移除）
- 启动类扫描 `com.panoramic` 以加载 common 中的字段自动填充等
- 异常语义（内部）：业务失败 `ServiceException` → 真实 HTTP 状态（如 400/403/404）+ `{code,msg}`；未知异常 → 500 `{code:500,msg:"系统异常"}`
