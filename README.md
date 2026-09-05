# 全景商城（Panoramic Mall）

基于微服务架构的电商项目，规划由**前端商城（C 端）**、**后端管理（B 端）**与**微服务后端**三部分组成。当前已完成后端基础设施、**登录与 RBAC 权限体系**、**商品中台**全链路，以及**商城店铺端一期（店铺管理）**。

## 系统架构

```
┌────────────────────────────────────────── 前端层 ──────────────────────────────────────────┐
│  admin（管理后台 :5173）    store（商城店铺端 :5174）    mall（商城前台，待开发）            │
└────────────┬──────────────────────────────────┬────────────────────────────────────────────┘
             │  /goods、/admin、/store、/discovery（Vite dev proxy → 网关 8080）
┌────────────▼──────────────────────────────────▼────────────────────────────────────────────┐
│  gateway  API 网关（:8080，Spring Cloud Gateway / WebFlux）                                  │
│    /goods/** ─▶ lb://goods-center    /admin/** ─▶ lb://admin    /store/** ─▶ lb://store-center│
└──────┬───────────────────────────────┬──────────────────────────────────────────────────────┘
       │  Nacos 服务注册与发现（:8848）
┌──────▼───────────────┐  ┌───────────▼──────────────┐  ┌──────────▼───────────────┐
│ goods-center 商品中心 │  │ admin 平台管理            │  │ store-center 店铺中心      │
│ （:8081）             │  │ （:8082）                 │  │ （:8083）                │
│  分类/品牌/SPU-SKU     │  │  登录 + RBAC + 店铺审核    │  │  店主账号 + 店铺 + 审核状态机│
└──────┬───────────────┘  └───────────┬──────────────┘  └──────────┬───────────────┘
       ▼                              ▼                             ▼
        MySQL 8（库 panoramic_mall：goods_* / sys_* / store_*）
```

> common 工具包被各业务服务共享；鉴权会话由 JWT `type` claim + Redis 键 `{前缀}:{userType}:{userId}` 区分平台管理员（admin）与店主（store）两套账号体系。

## 仓库结构

| 目录 | 说明 | 文档 |
|---|---|---|
| [backend/](backend/) | 微服务后端（Maven 多模块） | [README](backend/README.md) |
| ├── [common/](backend/common/) | 公共工具包（非服务）：返回结构/基类/异常/分页/自动填充/鉴权 | [README](backend/common/README.md) |
| ├── [gateway/](backend/gateway/) | API 网关（8080）：路由转发、前缀剥离、鉴权透传 | [README](backend/gateway/README.md) |
| ├── [goods-center/](backend/goods-center/) | 商品中心（8081）：商品中台业务 | [README](backend/goods-center/README.md) |
| ├── [admin/](backend/admin/) | 平台管理（8082）：登录 + 用户/角色/权限 + 店铺审核 | [README](backend/admin/README.md) |
| └── [store-center/](backend/store-center/) | 店铺中心（8083）：店主账号 + 店铺信息与审核 | [README](backend/store-center/README.md) |
| [frontend/](frontend/) | 前端（按项目拆分） | [README](frontend/README.md) |
| ├── [admin/](frontend/admin/) | 后端管理后台（5173）：分类/品牌/SPU、用户/角色/权限、店铺审核 | [README](frontend/admin/README.md) |
| ├── [store/](frontend/store/) | 商城店铺端（5174）：店主注册登录 + 店铺信息 + 开店入口 | [README](frontend/store/README.md) |
| └── [mall/](frontend/mall/) | 商城前台（占位，待开发） | [README](frontend/mall/README.md) |

## 技术栈

- **后端**：Java 21 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 · Nacos · MyBatis-Plus 3.5.16 · MySQL 8 · Lombok
- **前端**：Vue 3 · Vite 7 · Vue Router 4 · Element Plus · Axios（管理后台 / 店铺端共用一套设计令牌）
- 接口返回统一 `RespData{code,msg,data}`（成功 `code=200`），业务失败由后端返回中文提示（`code=400`）

## 已实现功能

1. **登录与 RBAC 权限体系**：账号密码登录（BCrypt），JWT + Redis 会话，`userType` 维度隔离平台/店主；用户/角色/权限管理，动态菜单（`/admin/permissions/menus` 按角色过滤）与按钮 `v-perm` 指令
2. **分类管理**：最多 3 级的多级分类树，新增/编辑/删除（有子分类或有商品时自动拦截）
3. **品牌管理**：品牌增删改查与分页关键字搜索，被商品引用时禁止删除
4. **商品管理（SPU/SKU）**：
   - 商品基础信息（名称/叶子分类/品牌/主图/轮播图/富文本详情）增删改查与分页筛选；允许 0 SKU
   - 商品为商城商品信息模板，以「展示/隐藏」表示对商城是否可见（展示中禁止删除）
   - 商品自带**规格属性配置**（各规格维度→可选项）；SKU 由独立「规格」入口维护，组合取值均来自该配置（全量替换，SKU 主键稳定，为后续价格/库存模块预留）
   - 只读**预览**：名称/分类完整链条/主图/轮播图/富文本详情/规格配置/SKU 明细一览
5. **店铺管理 + 店主端一期（商城店铺端）**：
   - 店主端独立项目（frontend/store）+ 独立账号（store_user，经网关 /store/** 走 store-center），**注册即登录**
   - 店主维护**店铺信息**并**提交审核**（基础信息 + 联系人 + 省市区地址 + 营业执照三要素），状态机：0草稿 → 1待审核 → 2已通过 / 3已驳回（可编辑重提）
   - admin 后台新增**店铺管理**目录：店铺列表/详情、**通过 / 驳回（填原因）**，`store:shop:list/audit` 权限控制
   - **仅审核通过**后店主端开放 商品管理/订单管理/库存管理 入口（本期为假页面占位，后续需求）
6. 逻辑删除、字段自动填充、统一异常处理等公共能力由 `common` 提供，业务模块零重复实现

## 环境依赖

| 组件 | 地址 | 说明 |
|---|---|---|
| JDK | 21 | 编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | ≥ 20.19 | 前端构建（Vite 7 要求） |
| Nacos | `127.0.0.1:8848`（nacos/nacos） | 服务注册与发现 |
| MySQL | 连接信息见后端配置 | 库 `panoramic_mall`；默认指向本机 `127.0.0.1:3306`（root/root），远程库通过环境变量 `MYSQL_HOST/MYSQL_PORT/MYSQL_USERNAME/MYSQL_PASSWORD/MYSQL_DB` 注入 |

## 快速开始

```bash
# 1. 启动外部依赖：Nacos、MySQL。
#    库与表用各模块的 db/schema.sql 创建（均 IF NOT EXISTS，可重复执行）：
#      goods-center → 商品表；admin → sys_* 权限表 + 权限种子；store-center → store_user / store_shop

# 2. 安装后端父 POM 与 common（首次或改动后）
cd backend && mvn -N install && mvn -pl common install

# 3. 启动后端服务（各一个终端；连远程 MySQL 时先注入环境变量，如
#    MYSQL_HOST=xxx MYSQL_PORT=3306 MYSQL_USERNAME=xxx MYSQL_PASSWORD=xxx MYSQL_DB=panoramic_mall）
mvn -pl gateway spring-boot:run        # 8080 网关
mvn -pl goods-center spring-boot:run   # 8081 商品中心
mvn -pl admin spring-boot:run          # 8082 平台管理
mvn -pl store-center spring-boot:run   # 8083 店铺中心

# 4. 验证链路
curl http://localhost:8080/discovery/services

# 5. 启动前端（各一个终端）
cd frontend/admin && npm install && npm run dev   # → http://localhost:5173
cd frontend/store && npm install && npm run dev   # → http://localhost:5174
```

## 开发路线（规划）

- [x] 后端基础设施（网关 / Nacos 注册 / common 工具包）
- [x] 商品中台（分类、品牌、SPU/SKU 管理）+ 管理后台页面
- [x] 登录与 RBAC 权限体系（用户/角色/权限 + 动态菜单/按钮）
- [x] 店铺管理 + 店主端一期（frontend/store 与 store-center，开店审核闭环）
- [ ] 商城前台项目（frontend/mall）
- [ ] 开店后业务：店主商品 / 订单 / 库存、价格库存、图片上传等（店主端已留占位入口）
