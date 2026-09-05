# 全景商城后端（panoramic-mall-backend）

全景商城微服务后端，基于 **Spring Boot 4.0.7 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0** 的 Maven 多模块工程，注册中心使用 **Nacos**。

## 模块一览

| 模块 | 类型 | 端口 | 说明 |
|---|---|---|---|
| [common](common/) | 工具包（非服务） | — | 通用返回结构、异常处理、实体基类、自动填充、鉴权会话等，供业务服务引用 |
| [gateway](gateway/) | 网关服务 | 8080 | Spring Cloud Gateway 响应式网关，统一入口、路由转发与鉴权透传 |
| [goods-center](goods-center/) | 业务服务 | 8081 | 商品中台：分类 / 品牌 / SPU-SKU 商品管理 |
| [admin](admin/) | 业务服务 | 8082 | 平台管理：账号登录、RBAC（用户/角色/权限/菜单）、店铺管理审核 |
| [store-center](store-center/) | 业务服务 | 8083 | 店铺中心：店主账号（注册即登录）+ 店铺信息与审核状态机 |

## 技术栈

- Java 21、Maven 3.9+
- Spring Boot 4.0.7（`common` 与 `goods-center` 使用 Servlet 技术栈，`gateway` 使用 WebFlux 响应式栈）
- Spring Cloud Gateway、Spring Cloud LoadBalancer
- Nacos 服务发现与注册（默认 `127.0.0.1:8848`，账号 `nacos/nacos`）
- MyBatis-Plus 3.5.16（`mybatis-plus-spring-boot4-starter`，Spring Boot 4 专用）——逻辑删除 + 字段自动填充 + 分页插件
- MySQL 8（库 `panoramic_mall`；表结构在各模块 `db/schema.sql`：`goods-center` 商品表、`admin` 的 `sys_*` 权限表 + 权限种子、`store-center` 的 `store_user/store_shop`，均 IF NOT EXISTS 幂等）
- 鉴权：JWT（含 `userType` claim）+ Redis 会话（键 `{前缀}:{userType}:{userId}`），网关验签后向下游透传 `X-User-Id`/`X-User-Type`；`admin` 与 `store` 为两套隔离的 id 空间

## 快速开始

### 0. 环境准备

- JDK 21、Maven 3.9+
- Nacos 单机启动（默认 8848，账号 `nacos/nacos`）
- MySQL 8 可用：`goods-center` 默认连接本机 `127.0.0.1:3306`（root/root，库 `panoramic_mall`）；连接远程库时通过环境变量注入，详见 `goods-center/README.md` 配置说明

### 1. 构建

```bash
cd backend

# 安装父 POM 与 common 到本地仓库（新增/修改了父 POM 或 common 后需重跑）
mvn -N install
mvn -pl common install

# 全量编译打包
mvn clean package
```

### 2. 启动（按依赖顺序，各开一个终端）

> 首次运行前先建库建表：分别执行 `goods-center` / `admin` / `store-center` 下 `db/schema.sql`（IF NOT EXISTS 可重复执行）。

```bash
mvn -pl gateway spring-boot:run        # 网关 8080
mvn -pl goods-center spring-boot:run   # 商品中心 8081
mvn -pl admin spring-boot:run          # 平台管理 8082
mvn -pl store-center spring-boot:run   # 店铺中心 8083
```

> 配置说明：`application.yml` 中的 `@nacos.server-addr@` 等占位符由 Maven 资源过滤在构建时替换为父 POM properties 中集中定义的版本/地址。

### 3. 验证

```bash
curl http://localhost:8080/discovery/services   # 网关探活：查看已注册服务
curl http://localhost:8080/goods/categories/tree # 经网关访问商品分类树
```

## 请求链路

```
admin 前端(5173) ──/goods、/admin、/store、/discovery──┐
store 前端(5174) ──/store─────────────────────────────┤
                                          gateway(8080)
   /goods/** ─▶ goods-center(8081)  /admin/** ─▶ admin(8082)
   /store/** ─▶ store-center(8083)
                                        │
                                        └── Nacos(8848) 服务注册与发现 ──▶ MySQL(库 panoramic_mall)
```

## 约定

- 接口统一返回 `RespData{code,msg,data}`，成功 `code=200`；业务失败 `code=400` 携带中文提示；系统异常 `code=500`
- 实体继承 `common` 的 `BaseEntity`（自动填充创建/更新时间与操作人、逻辑删除 `is_delete`），分页查询参数继承 `BasePageVO`
- 跨服务统一经网关（鉴权/前缀剥离）；业务模块间不直连，服务内跨实体只走对方 owner service
- 店主端接口（`/store/auth`、`/store/shops`）由 store-center 按归属 + 审核状态机守卫；平台管理接口（如 `/store/admin/shops/**`）以 `@PreAuthorize` + 权限串（`store:shop:list/audit`）控制，权限种子见 `admin/db/schema.sql` 与 `backfill-store-permission.sql`
- 详情见各模块 README（店铺模块见 [store-center/README.md](store-center/README.md)）。
