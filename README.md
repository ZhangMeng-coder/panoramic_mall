# 全景商城（Panoramic Mall）

基于微服务架构的电商项目，规划由**前端商城（C 端）**、**后端管理（B 端）**与**微服务后端**三部分组成。当前已完成后端基础设施与**商品中台**全链路（后端 API + 管理后台页面）。

## 系统架构

```
┌─────────────────────────────── 前端层 ───────────────────────────────┐
│  admin（管理后台 :5173）          mall（商城前台，待开发）            │
└──────────────┬────────────────────────────────────────────────────────┘
               │  /goods、/discovery（Vite dev proxy）
┌──────────────▼────────────────────────────────────────────────────────┐
│  gateway  API 网关（:8080，Spring Cloud Gateway / WebFlux）           │
│    /goods/** ──StripPrefix=1──▶ lb://goods-center                     │
└──────────────┬────────────────────────────────────────────────────────┘
               │ Nacos 服务注册与发现（:8848）
┌──────────────▼────────────────────────────────────────────────────────┐
│  goods-center  商品中心（:8081，Spring Boot Servlet）                 │
│    分类 / 品牌 / SPU-SKU 商品管理                                      │
│  common  公共工具包（RespData/BaseEntity/异常处理/自动填充…）          │
└──────────────┬────────────────────────────────────────────────────────┘
               ▼
        MySQL 8（库 panoramic_mall：goods_category / goods_brand / goods_spu / goods_sku）
```

## 仓库结构

| 目录 | 说明 | 文档 |
|---|---|---|
| [backend/](backend/) | 微服务后端（Maven 多模块） | [README](backend/README.md) |
| ├── [common/](backend/common/) | 公共工具包（非服务）：返回结构/基类/异常/分页/自动填充 | [README](backend/common/README.md) |
| ├── [gateway/](backend/gateway/) | API 网关（8080）：路由转发、前缀剥离、服务探活 | [README](backend/gateway/README.md) |
| └── [goods-center/](backend/goods-center/) | 商品中心（8081）：商品中台业务 | [README](backend/goods-center/README.md) |
| [frontend/](frontend/) | 前端（按项目拆分） | [README](frontend/README.md) |
| ├── [admin/](frontend/admin/) | 后端管理后台（5173）：分类/品牌/SPU 管理页面 | [README](frontend/admin/README.md) |
| └── [mall/](frontend/mall/) | 商城前台（占位，待开发） | [README](frontend/mall/README.md) |

## 技术栈

- **后端**：Java 21 · Spring Boot 4.0.7 · Spring Cloud 2025.1.2 · Spring Cloud Alibaba 2025.1.0.0 · Nacos · MyBatis-Plus 3.5.16 · MySQL 8 · Lombok
- **前端**：Vue 3 · Vite 7 · Vue Router 4 · Element Plus · Axios
- 接口返回统一 `RespData{code,msg,data}`（成功 `code=200`），业务失败由后端返回中文提示（`code=400`）

## 已实现功能（商品中台）

1. **分类管理**：最多 3 级的多级分类树，新增/编辑/删除（有子分类或有商品时自动拦截）
2. **品牌管理**：品牌增删改查与分页关键字搜索，被商品引用时禁止删除
3. **商品管理（SPU/SKU）**：
   - 商品基础信息（名称/叶子分类/品牌/主图/轮播图/富文本详情）增删改查与分页筛选；允许 0 SKU
   - 商品为商城商品信息模板，无上下架概念，以「展示/隐藏」表示对商城是否可见（展示中禁止删除）
   - 商品自带**规格属性配置**（各规格维度→可选项）；SKU 由独立「规格」入口维护，组合取值均来自该配置（全量替换，SKU 主键稳定，为后续价格/库存模块预留）；暂不含价格与库存字段
   - 只读**预览**：名称/分类完整链条/主图/轮播图/富文本详情/规格配置/SKU 明细一览
4. 逻辑删除、字段自动填充、统一异常处理等公共能力由 `common` 提供，业务模块零重复实现

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
# 1. 启动外部依赖：Nacos、MySQL（库与表用 goods-center 的 db/schema.sql 创建，IF NOT EXISTS 可重复执行）

# 2. 安装后端父 POM 与 common（首次或改动后）
cd backend && mvn -N install && mvn -pl common install

# 3. 启动后端两个服务（各一个终端；连远程 MySQL 时先注入环境变量，如
#    MYSQL_HOST=xxx MYSQL_PORT=3306 MYSQL_USERNAME=xxx MYSQL_PASSWORD=xxx MYSQL_DB=panoramic_mall）
mvn -pl gateway spring-boot:run       # 8080
mvn -pl goods-center spring-boot:run  # 8081

# 4. 验证链路
curl http://localhost:8080/discovery/services

# 5. 启动管理后台
cd frontend/admin && npm install && npm run dev   # → http://localhost:5173
```

## 开发路线（规划）

- [x] 后端基础设施（网关 / Nacos 注册 / common 工具包）
- [x] 商品中台（分类、品牌、SPU/SKU 管理）+ 管理后台页面
- [ ] 商城前台项目（frontend/mall）
- [ ] 价格 / 库存 / 订单等业务模块与权限体系
