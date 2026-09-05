# store-center — 店铺中心

全景商城店铺端（商城店铺端/店主侧）服务（Servlet 技术栈），端口 **8083**。当前阶段（Phase 1）实现**店铺管理**：

- 店主**独立账号体系**（`store_user`，与平台管理员 `sys_user` 分开，userType 维度隔离），支持**自助注册（注册即登录）**
- 店主维护**店铺信息**并**提交审核**（店铺基础信息 + 联系人 + 省市区地址 + 营业执照三要素）
- 平台管理员在 admin 侧经本服务的「店铺管理」做**审核通过/驳回（填原因）**

商品管理/订单管理/库存管理为后续需求（店主端已预留假页面占位），不在此服务内。

## 功能模块

### 1. 店主账号（独立 + 注册即登录）
- `store_user` 与 `sys_user` 各自独立、id 空间由共享鉴权 `userType`（admin/store）经 Redis 键 `{prefix}:{userType}:{userId}` 与 JWT `type` claim 隔离，不串上下文
- 店主账号**无 RBAC 角色/权限维度**：`LoginUser` 快照 roleIds/perms 为空，接口以「归属 + 审核状态机」守卫

### 2. 店铺信息与审核状态机
审核状态：**0草稿 → 1待审核 → 2已通过 / 3已驳回**（店主可编辑重提）。

| 状态 | 店主保存草稿 | 店主提交 | 平台审核 |
|---|---|---|---|
| 无店铺 | 新建草稿(0) | 提交(1) | — |
| 0 草稿 | 更新草稿(0) | 提交(1) | — |
| 1 待审核 | ❌ 审核中锁定 | ❌ 重复提交 | ✅ 通过(2)/驳回(3) |
| 2 已通过 | ❌ 信息锁定只读 | ❌ 无需提交 | — |
| 3 已驳回 | 回到草稿(0) 清留痕 | 重新提交(1) | — |

- **提交即校验完整资质**：联系人/电话/省市区+详细地址/营业执照名称/统一社会信用代码/执照照 均必填；保存草稿不强制
- **审核只对「待审核(1)」做条件更新**（`update ... where status=1`）：并发/重复审核时更新 0 行即拒绝，防重复审核
- 驳回必须填原因（`audit_remark`）；审核人/审核时间（`audit_by/audit_time`）仅留痕记录，不与平台用户表联查
- 店主店铺为**一对一**（`owner_user_id` 唯一）

## 数据库（库：`panoramic_mall`）

| 表 | 说明 |
|---|---|
| `store_user` | 店主账号（username/password BCrypt/nickname/phone/status） |
| `store_shop` | 店铺（owner_user_id 唯一 + 店铺资质字段 + 审核状态/留痕字段） |

建表脚本：`src/main/resources/db/schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复执行）。字段沿用 common `BaseEntity` 约定：主键自增 + `is_delete` 逻辑删除 + 创建/更新时间与操作人。

## 接口清单（经网关统一加 `/store` 前缀，服务内无前缀）

### 店主认证（`/auth`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册（注册即登录）→ `RespData<LoginResultVO>` |
| POST | `/auth/login` | 登录 → `RespData<LoginResultVO>` |
| POST | `/auth/logout` | 登出（删 Redis 登录上下文） |
| GET | `/auth/me` | 当前店主信息 |

### 店主店铺（`/shops`，归属 + 状态机守卫）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/shops/mine` | 我的店铺（未创建返回 null） |
| POST | `/shops/save` | 保存草稿（无店铺则新建） |
| POST | `/shops/submit` | 提交审核（完整资质校验） |

### 平台店铺管理（`/admin/shops`，受 RBAC 权限控制）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/shops` | 分页查询（status/keyword 筛选，回填店主账号） |
| GET | `/admin/shops/{id}` | 详情（回填店主账号） |
| POST | `/admin/shops/{id}/audit` | 审核 `{approved, auditRemark}` |

**权限字符串**：`store:shop:list`（店铺查询）、`store:shop:audit`（店铺审核）——由平台 `sys_permission` 种子 + 角色授权下发。

## 鉴权说明

- **店主**登录/注册走白名单（网关 `/store/auth/login,register` + 服务侧 `/auth/login,/auth/register`），签发 `type=store` 的 JWT
- **平台管理员**访问 `/store/admin/**`：携带 admin 类型登录态，服务侧经 common 安全链从 Redis `admin:<id>` 重建其权限，命中 `store:shop:*` 后放行
- 店主接口无 perms，靠 @PreAuthorize 之外的「归属(owner_user_id=当前登录店主) + userType=store + 状态机」守卫；平台接口再加 userType=admin 兜底

## 配置说明

- **数据源**：默认本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程/定制库请注入环境变量：
  `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`（占位符定义见 Nacos `datasource-mysql.yml`，账号密码勿写入代码或提交到仓库）
- MyBatis-Plus：主键自增、`is_delete` 逻辑删除、驼峰映射、SQL 日志打印（StdOutImpl，上线前移除）
- 启动类扫描 `com.panoramic` 以加载 common 中的全局异常处理、分页插件、字段自动填充与安全链
- 响应结构：成功 `code=200`；业务校验失败 `code=400`（如「店铺审核中，暂不可修改」「驳回时必须填写驳回原因」「店铺不存在或已被审核，请刷新后重试」等）
