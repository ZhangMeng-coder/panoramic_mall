# 全景商城（Panoramic Mall）

基于微服务架构的电商项目，规划由**前端商城（C 端）**、**后端管理（B 端）**与**微服务后端**三部分组成。当前已完成后端基础设施、**登录与 RBAC 权限体系**、**商品中台**全链路，**商城店铺端（店铺管理 + 在售商品管理）**，**商城前台工程**（`frontend/mall`，账号已接入 mall-bff，首页内容仍静态写死）与**商城前台 BFF（mall-bff，C 端顾客账号）**。后端已按 **BFF + 下沉域**分层收口：页面只经网关访问端 BFF（admin / store-bff / mall-bff），业务域（goods-center / store）不开放公网路由，仅由端 BFF 经注册中心内部 Feign 调用。

## 系统架构

```
┌───────────────────────────────────────── 前端层 ─────────────────────────────────────────┐
│  admin（管理后台 :5173）    store（商城店铺端 :5174）    mall（商城前台 :5175）          │
└────────────┬─────────────────────────────────┬───────────────────────────────────────────┘
             │  /admin、/store、/mall、/discovery（Vite dev proxy → 网关 8080）
┌────────────▼─────────────────────────────────▼───────────────────────────────────────────┐
│  gateway  API 网关（:8080，Spring Cloud Gateway / WebFlux）                              │
│    公网入口 = 端 BFF 白名单（BffRouteGuardFilter 强制，域服务一律 403）                  │
│      /admin/** ─▶ lb://admin        /store/** ─▶ lb://store-bff                          │
│      /mall/**  ─▶ lb://mall-bff     /discovery/** 探活（不在路由内）                     │
└───────┬─────────────────────────────────────────┬────────────────────────────────────────┘
       │ Nacos 注册发现(:8848)                   │ 内部 Feign（X-User-Id/X-User-Type + 熔断）
┌───────────────────────────────────────────┐  ┌───────────────────────────────────────────┐
│  端 BFF 层（账号表 + type 的 JWT）        │  │  纯域层（不鉴权、无 Redis、无公网路由）   │
│  admin（:8082）平台账号 + RBAC + 编排     │  │  goods-center（:8081）分类/品牌/SPU-SKU   │
│  store-bff（:8084）店主账号 + 店铺商品    │  │  store（:8083）store_shop + 在售商品      │
│  mall-bff（:8085）顾客账号（一期不调域）  │  │  （域端口只在内网可达是安全前提）         │
└───────────────────────────────────────────┘  └───────────────────────────────────────────┘
              MySQL 8（库 panoramic_mall：goods_* / sys_* / store_* / mall_*）
```

> `common` 是被所有服务共享的纯基座；鉴权装配（JWT/Redis/安全链）单独放在 `common-auth`，**只有端 BFF 依赖它**——业务域结构上拿不到认证链，因此不鉴权、不碰 Redis。登录会话由 JWT `type` claim + Redis 键 `panoramic:login:{type}:{userId}` 区分平台管理员（admin）/ 店主（store）/ C 端顾客（user）三套账号体系，三端各自签发自己 `type` 的令牌。业务域（goods-center / store）与端 BFF（admin / store-bff）之间经 `com.panoramic.common.*.api` 同源 Feign 客户端互调（DTO/VO 上移 common、只透传身份头 + 熔断降级；域内不做权限判断）。⚠ **mall-bff 一期只做顾客账号、不调任何业务域**（无 Feign 客户端），首页数据聚合是二期。审计字段 `create_user`/`update_user` 为 `VARCHAR(32)`，值 `UserType:UserId`。

## 仓库结构

| 目录 | 说明 | 文档 |
|---|---|---|
| [backend/](backend/) | 微服务后端（Maven 多模块） | [README](backend/README.md) |
| ├── [common/](backend/common/) | 公共基座（非服务）：返回结构/基类/异常/分页/审计自动填充/登录用户模型 + 各域 Feign 客户端与共享 DTO/VO | [README](backend/common/README.md) |
| ├── [common-auth/](backend/common-auth/) | 鉴权装配层（非服务）：JWT + Redis 登录态 + 安全过滤链；**只被端 BFF 依赖**，业务域拿不到（故不鉴权） | [README](backend/common-auth/README.md) |
| ├── [gateway/](backend/gateway/) | API 网关（8080）：路由转发、前缀剥离、鉴权透传、端 BFF 白名单 | [README](backend/gateway/README.md) |
| ├── [goods-center/](backend/goods-center/) | 商品域（8081，下沉纯域）：标准商品中台 | [README](backend/goods-center/README.md) |
| ├── [store/](backend/store/) | 店铺域（8083，下沉纯域）：店铺 store_shop + 审核状态机 + 在售商品 store_goods_* | [README](backend/store/README.md) |
| ├── [store-bff/](backend/store-bff/) | 店铺端 BFF（8084）：店主账号 store_user + 店铺资料/在售商品编排 | [README](backend/store-bff/README.md) |
| ├── [mall-bff/](backend/mall-bff/) | 商城前台 BFF（8085）：C 端顾客账号 mall_user（手机号 + 模拟短信验证码，签发 type=user）；一期不调业务域 | [README](backend/mall-bff/README.md) |
| ├── [admin/](backend/admin/) | 平台管理（8082，端 BFF）：登录 + 用户/角色/权限 + 店铺审核 + 店铺商品管理 | [README](backend/admin/README.md) |
| [frontend/](frontend/) | 前端（按项目拆分；三端统一 Vue 3 + Vite + TypeScript + axios） | [README](frontend/README.md) |
| ├── [admin/](frontend/admin/) | 后端管理后台（5173）：分类/品牌/SPU、用户/角色/权限、店铺审核与店铺商品管理 | [README](frontend/admin/README.md) |
| ├── [store/](frontend/store/) | 商城店铺端（5174）：店主注册登录 + 店铺信息 + 在售商品管理 | [README](frontend/store/README.md) |
| └── [mall/](frontend/mall/) | 商城前台（5175）：**账号已接入 mall-bff**（登录 / 注册 / 退出 / me），首页内容静态写死 | [README](frontend/mall/README.md) |
| [docs/contracts/](docs/contracts/) | **对外契约清单**（跨前后端）：页面级 / 内部 Feign / 跨服务隐式三层契约 + 静态漂移检查器 | [README](docs/contracts/README.md) |

## 技术栈

- **后端**：Java 21 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 · Nacos · MyBatis-Plus 3.5.16 · MySQL 8 · Lombok
- **前端**：Vue 3 · Vite 7 · Vue Router 4 · **TypeScript（三端统一，`strict`）** · Element Plus（管理后台 / 店铺端）· **Axios**（三端统一 HTTP 客户端，各自 `src/api/request.ts` 单入口）
  - 三端 `npm run build` = `vue-tsc --noEmit && vite build`，**类型不过即构建失败**（另有 `npm run type-check` 只查类型）；三份 `tsconfig.json` 一致，唯一差异是 admin / store 的 `types` 多一项 `element-plus/global`
  - 管理后台 / 店铺端共用一套设计令牌；商城前台是**另一套**（C 端促销风橙红），见 `frontend/mall/src/styles/tokens.css`
- 页面接口统一返回 `RespData{code,msg,data}`（成功 `code=200`）；内部域接口不包 RespData、直接返回业务类型，错误转真实 HTTP 状态 + `{code,msg}` 由 Feign ErrorDecoder 还原

## 已实现功能

1. **登录与 RBAC 权限体系**：账号密码登录（BCrypt），JWT + Redis 会话，`userType` 维度隔离平台/店主；用户/角色/权限管理，动态菜单（`/admin/permissions/menus` 按角色过滤）与按钮 `v-perm` 指令
2. **分类管理**：最多 3 级的多级分类树，新增/编辑/删除（有子分类或有商品时自动拦截）
3. **品牌管理**：品牌增删改查与分页关键字搜索，被商品引用时禁止删除
4. **商品管理（SPU/SKU）**：
   - 商品基础信息（名称/叶子分类/品牌/主图/轮播图/富文本详情）增删改查与分页筛选；允许 0 SKU
   - 商品为商城商品信息模板，以「展示/隐藏」表示对商城是否可见（展示中禁止删除）
   - 商品自带**规格属性配置**（各规格维度→可选项）；SKU 由独立「规格」入口维护，组合取值均来自该配置（全量替换，SKU 主键稳定，为后续价格/库存模块预留）
   - 只读**预览**：名称/分类完整链条/主图/轮播图/富文本详情/规格配置/SKU 明细一览
5. **店铺管理 + 店主端一期（商城店铺端，BFF 化已落地）**：
   - 店主端独立项目（frontend/store）+ 独立账号（store_user 归 store-bff，经网关 /store/** 登录；店铺数据归 store 域），**注册即登录**
   - 店主维护**店铺信息**并**提交审核**（基础信息 + 联系人 + 省市区地址 + 营业执照三要素），状态机：0草稿 → 1待审核 → 2已通过 / 3已驳回（可编辑重提）；「账号店同 ID」（store_shop.id == 店主账号 id，一人一店）
   - admin 后台**店铺管理**目录（经 admin BFF → store 域 platform 接口）：店铺列表/详情、**通过 / 驳回（填原因）**，`store:shop:list/audit` 权限控制；不显示店主登录账号
   - **仅审核通过**后店主端开放 商品管理/订单管理/库存管理 入口（店铺未过审时页面与接口均 403）
   - **店主在售商品管理**（frontend/store「商品管理」，经 store-bff → store 域 owner 接口）：商品增删改查与分页筛选（分类/品牌/关键字/上下架）、规格属性配置、SKU 明细维护与**按 SKU 上下架**；**上架任一 SKU → 商品自动上架，SKU 全下架 → 商品自动下架**（SPU 状态为推导结果、只读）；已上架 SKU 整行锁定（须先下架才能改/删），存在上架 SKU 时商品规格属性配置只读、商品不可删除
   - **中台模板关联与版本同步**：新增时可按中台 `sku_code` 整单预填；中台 SPU/SKU 均带版本戳 `version`（任何修改即刷新），店主编辑关联商品时 BFF 比对中台版本 → 不一致提示「中台模板已更新」并给「同步」按钮（覆盖商品信息与规格，SKU 按 `sku_code` 对齐、保留已填价格），**不同步也能保存**；中台模板已删则提示「已不存在」
6. **管理后台店铺商品管理 + 商品类别全路径（2026-09-12）**：
   - admin 后台「店铺管理 → 店铺商品」目录：**全店铺**在售商品列表，按 **类型（分类，含全部子分类的子树匹配）/ 品牌 / 店铺 / 上下架 / 锁定状态 / 名称关键字** 查询（`store:goods:list`），并提供**只读详情页**（基础信息 / 图片 / 富文本详情 / 规格配置 / SKU 明细 / 锁定信息）
   - **平台锁定 / 解锁**（`store:goods:lock`，锁定原因必填）：锁定即把该商品全部 SKU 级联下架、商品随之推导为下架；**锁定期店主端整行只读**（编辑/上下架/增删改 SKU/删除一律拒绝，域内强制）；解锁只清锁定字段、**不自动恢复上架**（店主手动重上）。店主端只读呈现：列表锁定状态列 + 「锁定信息」弹窗（**只显示原因与时间，不显示锁定人**）
   - **商品类别展示改为全路径**（如「服饰 / 男装 / T恤」）：admin 标准商品列表、admin 店铺商品列表、店铺端商品列表一致；路径由端 BFF **读时**调 goods-center 批量路径接口解析（域不持分类表、只存快照），解析失败自动回退快照分类名
7. **商城前台 BFF 一期（C 端顾客账号，2026-09-14）**：
   - 新建 `backend/mall-bff`（8085，第三套身份 `type=user`），网关新增 `/mall/**` 路由并纳入端 BFF 白名单，**5 条接口**：取码 / 注册 / 登录 / 登出 / 当前登录顾客（经网关 `/mall/auth/**`）
   - **账号即手机号**：`phone` 既是登录账号也是唯一键；验证方式为**手机号 + 短信验证码**，无密码
   - ⚠ 短信为**模拟实现**：取码接口只写一行日志，**不发真实短信、不落库、不落 Redis**，校验与固定码 `888888` 比对（配置项 `panoramic.mall.sms-fixed-code`）
   - 注册即登录（`mall_user` 建号后直接签发 JWT + Redis 会话 `panoramic:login:user:{id}`）；C 端**不接 RBAC**（无 `@PreAuthorize`，与店主端同理）
   - **一期不调任何业务域**（无 Feign 客户端）；首页数据聚合为二期
   - **mall 前台已接入**（2026-09-14）：`frontend/mall` 的 `/login` 与 `/register` 两页打通取码 / 注册 / 登录，顶栏接真实登出，刷新由路由守卫拉 `/auth/me` 重建登录态；**首页六区块仍读静态 `src/mock/`**
8. 逻辑删除、字段自动填充、统一异常处理等公共能力由 `common` 提供，业务模块零重复实现

## 环境依赖

| 组件 | 地址 | 说明 |
|---|---|---|
| JDK | 21 | 编译与运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | ≥ 20.19 | 前端构建（Vite 7 要求） |
| Nacos | `127.0.0.1:8848`（nacos/nacos） | 服务注册与发现 |
| MySQL | 连接信息由 Nacos 共享配置 `datasource-mysql.yml` 提供 | 库 `panoramic_mall`；默认指向 `123.56.117.17:3306`（root/root），其他库通过环境变量 `MYSQL_HOST/MYSQL_PORT/MYSQL_USERNAME/MYSQL_PASSWORD/MYSQL_DB` 注入 |

## 快速开始

```bash
# 1. 启动外部依赖：Nacos、MySQL。
#    库与表用各模块的 db/schema.sql 创建（均 IF NOT EXISTS，可重复执行）：
#      goods-center → goods_*；admin → sys_* 权限表 + 权限种子；store → store_shop + store_goods_*；store-bff → store_user；mall-bff → mall_user
#    另需把 backend/nacos-config/ 下的共享配置发布到 Nacos（服务侧 import 不带 optional:，缺任一则启动失败）：
#      datasource-mysql.yml / datasource-redis.yml / auth.yml / feign-circuitbreaker.yml → 见 backend/nacos-config/README.md

# 2. 安装后端父 POM 与 common / common-auth（首次或改动后）
cd backend && mvn -N install && mvn -pl common,common-auth install

# 3. 启动后端服务（各一个终端；连远程 MySQL 时先注入环境变量，如
#    MYSQL_HOST=xxx MYSQL_PORT=3306 MYSQL_USERNAME=xxx MYSQL_PASSWORD=xxx MYSQL_DB=panoramic_mall）
mvn -pl gateway spring-boot:run        # 8080 网关
mvn -pl goods-center spring-boot:run   # 8081 商品域（纯域）
mvn -pl admin spring-boot:run          # 8082 平台管理（端 BFF）
mvn -pl store spring-boot:run          # 8083 店铺域（纯域）
mvn -pl store-bff spring-boot:run      # 8084 店铺端 BFF
mvn -pl mall-bff spring-boot:run       # 8085 商城前台 BFF（C 端）

# 4. 验证链路
curl http://localhost:8080/discovery/services

# 5. 启动前端（各一个终端）
cd frontend/admin && npm install && npm run dev   # → http://localhost:5173
cd frontend/store && npm install && npm run dev   # → http://localhost:5174
cd frontend/mall  && npm install && npm run dev   # → http://localhost:5175
```

## 开发路线（规划）

- [x] 后端基础设施（网关 / Nacos 注册 / common 工具包）
- [x] 商品中台（分类、品牌、SPU/SKU 管理）+ 管理后台页面
- [x] 登录与 RBAC 权限体系（用户/角色/权限 + 动态菜单/按钮）
- [x] 店铺管理 + 店主端一期（frontend/store，开店审核闭环）
- [x] 店主端商品管理（在售商品 SPU/SKU 增删改、按 SKU 上下架联动、中台模板关联与版本同步）
- [x] BFF 化收口：goods-center 下沉纯域、store-center 拆为 store（域）+ store-bff（店铺端 BFF）、admin 店铺管理 BFF 编排、网关公网收敛为端 BFF 白名单
- [x] 管理后台店铺商品管理（全店铺查询/只读详情/平台锁定解锁，分类子树筛选）+ 商品类别全路径展示
- [x] 商城前台工程（frontend/mall）：Vue 3 + Vite + TypeScript，首页六区块 + 静态数据
- [x] 商城前台 BFF 一期（mall-bff）：C 端顾客账号（手机号 + 模拟短信验证码，签发 `type=user`）+ 网关 `/mall/**` 路由
- [x] mall 前台接入账号接口：登录 / 注册两页 + 顶栏登录态 + 刷新重建（首页数据仍静态）
- [x] 前端三端技术形态拉平：admin / store 由 Vue 3 + JS 转为 **Vue 3 + TypeScript（`strict`）**，与 mall 统一为 Vue 3 + Vite + TS + axios，`vue-tsc` 挂进三端构建
- [ ] mall-bff 二期：首页数据聚合（经 Feign 调 goods-center 的商品/分类）、`frontend/mall` 首页接入接口
- [ ] trade-center 下沉（购物车 / 订单 / 评价）
- [ ] 开店后其余业务：店主订单 / 库存、价格库存、图片上传等（店主端已留占位入口）
