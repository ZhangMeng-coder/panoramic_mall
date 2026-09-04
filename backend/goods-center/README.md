# goods-center — 商品中心

全景商城商品中台服务（Servlet 技术栈），端口 **8081**，当前阶段实现商品基础数据管理（不含价格/库存，不含权限），供管理后台使用。

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
- **SPU 管理（基础信息 + 规格属性配置）**：新增/编辑/删除/展示·隐藏、分页筛选（分类/品牌/展示状态/名称关键字）、详情（含 SKU、规格属性配置与分类完整链条）。商品为商城商品信息模板，无上下架概念，以 **展示/隐藏** 表示对商城是否可见；允许先保存基础信息、暂不维护 SKU（0 SKU）
  - 商品必须挂在**叶子分类**下；展示中的商品禁止删除
  - **规格属性配置**（`spec_config`）：定义各规格维度及其可选项（如 `[{"spec":"颜色","values":["黑色","白色"]}]`）；更新基础信息时不得移除仍被存量 SKU 使用的规格/属性值（防孤立守卫），SKU 的调整需走「规格」管理
- **SKU 独立管理**（规格属性组合，无价格/库存字段）：
  - 通过 `PUT /spu/{id}/skus` **全量替换**；空 `skus` = 清空该商品全部 SKU
  - 组合必须来自该商品的规格属性配置（各维度取一个值，与配置逐项匹配），否则拒绝
  - diff 策略：入参带 `id` 的 SKU 校验归属后更新、`id` 为空的新 SKU 插入、缺失的存量 SKU 逻辑删除 —— 保证已存在 SKU 的 `id` 稳定（供后续价格/库存模块引用），整体事务回滚
- 商品轮播图（`image_list`）、规格属性配置（`spec_config`）与 SKU 规格（`spec_attrs`）以 **JSON 列**存储，服务层 Jackson 转换

## 数据库（库：`panoramic_mall`）

| 表 | 说明 |
|---|---|
| `goods_category` | 商品分类（多级树，parent_id 自关联） |
| `goods_brand` | 品牌 |
| `goods_spu` | 商品 SPU（含 image_list、spec_config JSON 列） |
| `goods_sku` | 商品 SKU 规格组合（含 spec_attrs JSON 列） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。字段沿用 common `BaseEntity` 约定：主键自增 + `is_delete` 逻辑删除 + 创建/更新时间与操作人。

## 接口清单（经网关统一加 `/goods` 前缀，服务内无前缀）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/categories` | 新建分类 → `RespData<Long>` |
| GET | `/categories/tree` | 全量分类树 |
| PUT | `/categories/{id}` | 修改分类（名称/排序，不支持移动父级） |
| DELETE | `/categories/{id}` | 删除（有子分类/有商品则拒绝） |
| POST | `/brands` | 新建品牌 → `RespData<Long>` |
| PUT | `/brands/{id}` | 更新品牌 |
| DELETE | `/brands/{id}` | 删除（被商品引用则拒绝） |
| GET | `/brands/page` | 分页查询（pageNum/pageSize/keyword） |
| GET | `/brands/list` | 全量列表（表单下拉用） |
| GET | `/brands/{id}` | 品牌详情 |
| POST | `/spu` | 新建商品（基础信息+规格属性配置）→ `RespData<Long>` |
| PUT | `/spu/{id}` | 更新商品（仅基础信息+规格属性配置，防孤立守卫） |
| PUT | `/spu/{id}/skus` | 全量替换 SKU（组合须匹配规格属性配置，空 `skus`=清空） |
| PUT | `/spu/{id}/status` | 展示/隐藏切换 `{status: 0|1}` |
| DELETE | `/spu/{id}` | 删除（展示中拒绝，级联逻辑删除 SKU） |
| GET | `/spu/page` | 分页查询（categoryId/brandId/status/keyword，回填分类/品牌名） |
| GET | `/spu/{id}` | 商品详情（含 skus、规格属性配置、分类完整链条） |

## 配置说明

- **数据源**：默认本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程/定制库请注入环境变量：
  `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见 `application.yml`，账号密码勿写入代码或提交到仓库）
- MyBatis-Plus：主键自增、`is_delete` 逻辑删除、驼峰映射、SQL 日志打印（StdOutImpl，上线前移除）
- 启动类扫描 `com.panoramic` 以加载 common 中的全局异常处理、分页插件与字段自动填充
- 响应结构：成功 `code=200`；业务校验失败 `code=400`（如「存在子分类，无法删除」「商品展示中，请先隐藏再删除」「SKU 不存在或不属于该商品」等）
