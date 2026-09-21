# goods-center — 标准商品平台（商品域，下沉纯域）

全景商城**标准商品平台 / 标准商品模板库**（Servlet 技术栈），端口 **8081**，实现分类 / 品牌 / 标准 SPU-SKU 模板的基础数据管理。

模板**无价格、无库存**，语义为「标准商品模板」，区别于 store 域的「店铺在售商品」——这是本仓库最容易加错表的地方，见根 `CLAUDE.md` 的「术语防呆」。

## 一、架构位置

**下沉纯域（第 ② 层）**：不向页面暴露公网路由，只被各端 BFF 经注册中心**内部 Feign** 调用。

| 方向 | 对象 | 通道 |
|---|---|---|
| 被谁调 | admin BFF（商品模板管理） | `goods-center-interface` 的 `GoodsCenterClient`，带熔断降级 |
| 被谁调 | store-bff（分类树 / 品牌下拉 / 按 SKU 编码反查模板） | 同上 |
| 被谁调 | mall-bff（C 端分类树） | 同上 |
| 本域调谁 | — | **不启用 Feign 客户端，纯被调方** |

- 本域只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链与 Redis；⚠ 域端口只在内网可达是**安全前提**，本域不做鉴权，防线在网络层、不在应用层。

> 📋 对外接口清单见 [`docs/contracts/goods-center.md`](../../docs/contracts/goods-center.md)（条数与落地状态以该表为准，本 README 不另记）。
> 本 README 只讲**这服务是什么、持什么、做什么**；接口、形状、类型位置一律不在此处重复。

## 二、实体标记

库：`panoramic_mall`（与其它服务同库，只分表所有权）。

| 表 | 说明 |
|---|---|
| `goods_category` | 商品分类（多级树，parent_id 自关联） |
| `goods_brand` | 品牌 |
| `goods_spu` | 商品 SPU（含 image_list、spec_config JSON 列 + 版本戳 `version`） |
| `goods_sku` | 商品 SKU 规格组合（含 spec_attrs JSON 列 + 版本戳 `version`） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。⚠ 建库只有一个入口：历次结构变更的**最终形状**都已写进该文件，不再保留中间迁移脚本。

实体沿用 common `BaseEntity`（主键自增 + 逻辑删除 + 审计字段自动填充，取值格式见 `CLAUDE.md`「代码生成与分层约定」）。

## 三、职责与边界

### 1. 分类管理（多级树）

- 分类树**最多 3 级**（新建按父级自动算层级，超 3 级拒绝）；同级下分类名不可重复（含更新场景）；树查询全量返回、按 `sort` 排序
- 删除保护：存在子分类或分类下存在商品时拒绝删除（逻辑删除）
- 分类**全路径**（如「服饰 / 男装 / T恤」）由本域按 id 批量解析后返回，**域内不落库、不缓存**

### 2. 品牌管理

- 品牌 CRUD + 关键字分页查询（`keyword` 模糊匹配名称）；列表接口供商品表单下拉使用
- 删除保护：品牌下存在商品时拒绝删除

### 3. SPU / SKU 商品管理

**SPU（基础信息 + 规格属性配置）**：

- 新增/编辑/删除/展示·隐藏，分页筛选（分类 / 品牌 / 展示状态 / 名称关键字），详情含 SKU、规格属性配置与分类完整链条
- SPU 是商城**标准商品信息模板**，**无上下架概念**，以**展示/隐藏**表示对商城是否可见；允许先存基础信息、暂不维护 SKU（0 SKU）
- 必须挂在**叶子分类**下；展示中的商品禁止删除
- **规格属性配置（`spec_config`）**：定义各规格维度及其可选项（如 `[{"spec":"颜色","values":["黑色","白色"]}]`）；更新基础信息时不得移除仍被存量 SKU 使用的规格/属性值（**防孤立守卫**），SKU 的调整需走「规格」管理

**SKU（规格属性组合，无价格/库存字段）**：

- 通过「全量替换」接口整体提交；空 `skus` = 清空该 SPU 全部 SKU
- 组合必须来自该 SPU 的规格属性配置（各维度取一个值，与配置逐项匹配），否则拒绝
- diff 策略：入参带 `id` 的 SKU 校验归属后更新、`id` 为空的新 SKU 插入、缺失的存量 SKU 逻辑删除 —— 保证已存在 SKU 的 `id` 稳定（供后续价格/库存模块引用），整体事务回滚

**存储与版本**：

- 轮播图（`image_list`）、规格属性配置（`spec_config`）与 SKU 规格（`spec_attrs`）以 **JSON 列**存储，服务层 Jackson 转换
- **版本戳 `version`（`BIGINT`，Unix 毫秒）**：`goods_spu` 与 `goods_sku` 各一列，本行**任何修改即刷新**（SPU 自身字段变更、或其任一 SKU 变更，都会刷新该 SPU 的 `version`）。用途：店铺端关联中台模板后记录该戳，编辑时比对判断「中台模板是否已更新」。⚠ **比对与提示在 store-bff 编排侧做，本域不做版本判断**，只落库/回读该戳

**按 SKU 编码反查 SPU**（供店铺端「新增商品时按中台编码整单预填」）：`goods_sku.sku_code` **无唯一索引**；命中多条按 `id asc` 取第一条并回传 `matchedSkuCount` 提示；未命中返回 `spu=null`（**成功响应**，不是错误）。

### 4. 边界（本域不做什么）

- **不做任何鉴权、不做任何权限判断、不校验 token**：唯一授权点是调用方端 BFF 的 `@PreAuthorize`；**不装配认证链**，不打 Redis、不查登录态
- 身份头只**读不判**：`X-User-Id` / `X-User-Type` 仅用于审计字段自动填充；**缺头即不填充、放行**，不回 401（回 401 等于在域内做鉴权）
- **不调中台、不判版本**：版本比对属 store-bff 的编排职责

## 四、配置说明

- **数据源**：连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供（默认指向 `123.56.117.17:3306`，库 `panoramic_mall`）；连接其他库请注入环境变量 `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` / `MYSQL_USERNAME` / `MYSQL_PASSWORD`（账号密码勿写入代码或提交到仓库）
- **Nacos 共享配置**：只引入 `datasource-mysql.yml`，且 import **不带 `optional:`**——缺该 dataId 则启动失败。加载矩阵见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条
- MyBatis-Plus：主键自增、`is_delete` 逻辑删除、驼峰映射、SQL 日志打印（StdOutImpl，上线前移除）
- 启动类扫描 `com.panoramic` 以加载 common 中的字段自动填充等
- 异常语义（内部）：业务失败 `ServiceException` → 真实 HTTP 状态（如 400/403/404）+ `{code,msg}`，由 `GoodsDomainExceptionHandler` 产出；未知异常 → 500 `{code:500,msg:"系统异常"}`
