# nacos-config — Nacos 共享配置（源文件）

本目录是**Nacos 配置中心各 data-id 的版本源**：内容在此维护、提交入库，再由人工发布到 Nacos。
**改完这里必须同步发布到 Nacos 才生效**（两边不一致时的表现是「改了没反应」，历史上多次踩到）。

本目录**不是可运行服务**：无端口、无 `pom`、无实体。它的职责只有一个 —— 做共享配置的唯一版本源。

## 一、data-id 一览（各含什么）

| data-id | 内容 |
|---|---|
| `datasource-mysql.yml` | MySQL 数据源 + mybatis-plus |
| `datasource-redis.yml` | Redis 连接 |
| `auth.yml` | `jwt-secret` / `jwt-expire-seconds` / `redis-prefix` / `header-name` |
| `feign-circuitbreaker.yml` | Feign 熔断开关 + resilience4j 参数 |

> 📋 **谁加载什么**（加载矩阵）见 [`docs/contracts/cross-cutting.md`](../../docs/contracts/cross-cutting.md) 第 12 条 —— 那是跨服务契约，不在本文件重复。
> 划分依据：**gateway 不加载 `datasource-mysql`**（无数据源）；**业务域 goods-center / store 只加载 `datasource-mysql`**（域服务不鉴权、不碰登录态、不走 Feign 客户端）；**`feign-circuitbreaker` 只被端 BFF 加载**（只有端 BFF 出站调域）。

## 二、约定

- 服务侧 `spring.config.import` **不带 `optional:` 前缀**：配置中心不可用、或任一 data-id 缺失，服务**启动即失败**，不做静默降级（本地开发环境，Nacos 本身就是必需依赖）。
- GROUP 一律 `DEFAULT_GROUP`；当前未启用 namespace 隔离。
- **本目录的文件名即 data-id**，改名等于改契约（各服务的 `import` 列表会立刻失配）。

## 三、发布方式

控制台：`http://127.0.0.1:8848/nacos`（`nacos/nacos`）→ 配置管理 → 新建/编辑配置，data-id 与 GROUP 照上表，内容直接粘贴本目录对应文件。

> ⚠ **新建 data-id 后，先发布再启动服务**——因为 import 不带 `optional:`，缺配置会直接起不来。
>
> ⚠ 本目录与 Nacos 控制台的一致性**无法静态核对**，属人工发布纪律。
