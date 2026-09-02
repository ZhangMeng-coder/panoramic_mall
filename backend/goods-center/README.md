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
- **SPU 管理**：新增/编辑/删除/上下架、分页筛选（分类/品牌/状态/名称关键字）、详情
  - 商品必须挂在**叶子分类**下；上架中的商品禁止删除
- **SKU 级联维护**（规格属性组合，无价格/库存字段）：
  - 新增时随 SPU 一起保存，校验同请求内 SKU 规格组合不重复
  - 更新采用 **diff 策略**：入参带 `id` 的 SKU 校验归属后更新、`id` 为空的新 SKU 插入、缺失的 SKU 逻辑删除 —— 保证已存在 SKU 的 `id` 稳定（供后续价格/库存模块引用），整体事务回滚
  - 编辑回显由前端按 SKU 规格反推规格维度（见管理后台）
- 商品轮播图（`image_list`）与 SKU 规格（`spec_attrs`）以 **JSON 列**存储，服务层 Jackson 转换

## 数据库（库：`panoramic_mall`）

| 表 | 说明 |
|---|---|
| `goods_category` | 商品分类（多级树，parent_id 自关联） |
| `goods_brand` | 品牌 |
| `goods_spu` | 商品 SPU（含 image_list JSON 列） |
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
| POST | `/spu` | 新建商品（含 skus）→ `RespData<Long>` |
| PUT | `/spu/{id}` | 更新商品（SKU diff 级联） |
| PUT | `/spu/{id}/status` | 上下架 `{status: 0|1}` |
| DELETE | `/spu/{id}` | 删除（上架中拒绝，级联逻辑删除 SKU） |
| GET | `/spu/page` | 分页查询（categoryId/brandId/status/keyword，回填分类/品牌名） |
| GET | `/spu/{id}` | 商品详情（含 skus） |

## 配置说明

- **数据源**：默认本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程/定制库请注入环境变量：
  `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见 `application.yml`，账号密码勿写入代码或提交到仓库）
- MyBatis-Plus：主键自增、`is_delete` 逻辑删除、驼峰映射、SQL 日志打印（StdOutImpl，上线前移除）
- 启动类扫描 `com.panoramic` 以加载 common 中的全局异常处理、分页插件与字段自动填充
- 响应结构：成功 `code=200`；业务校验失败 `code=400`（如「存在子分类，无法删除」「请先下架再删除」「SKU 不存在或不属于该商品」等）
