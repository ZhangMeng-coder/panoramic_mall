# nacos-config — Nacos 共享配置（源文件）

本目录是**Nacos 配置中心各 data-id 的版本源**：内容在此维护、提交入库，再由人工发布到 Nacos。
**改完这里必须同步发布到 Nacos 才生效**（两边不一致时的表现是「改了没反应」，历史上多次踩到）。

- 服务侧 `spring.config.import` **不带 `optional:` 前缀**：配置中心不可用、或任一 data-id 缺失，服务**启动即失败**，不做静默降级（本地开发环境，Nacos 本身就是必需依赖）。
- GROUP 一律 `DEFAULT_GROUP`；当前未启用 namespace 隔离。

## data-id 一览（谁加载什么）

| data-id | 内容 | 加载方 |
|---|---|---|
| `datasource-mysql.yml` | MySQL 数据源 + mybatis-plus | admin、store-bff、goods-center、store |
| `datasource-redis.yml` | Redis 连接 | admin、store-bff、gateway |
| `auth.yml` | `jwt-secret` / `jwt-expire-seconds` / `redis-prefix` / `header-name` | admin、store-bff、gateway |
| `feign-circuitbreaker.yml` | Feign 熔断开关 + resilience4j 参数 | admin、store-bff |

划分依据：

- **gateway 不加载 `datasource-mysql.yml`**——网关无数据源。
- **业务域（goods-center / store）只加载 `datasource-mysql.yml`**——域服务不做鉴权、不碰登录态（不依赖 `common-auth`，结构上拿不到认证链与 Redis），也不走 Feign 客户端，故不引入其余三个。
- **`feign-circuitbreaker.yml` 只被端 BFF 加载**——只有端 BFF 出站调业务域。

## 发布方式

控制台：`http://127.0.0.1:8848/nacos`（`nacos/nacos`）→ 配置管理 → 新建/编辑配置，data-id 与 GROUP 照上表，内容直接粘贴本目录对应文件。

> 新建 data-id 后，**先发布再启动服务**——因为 import 不带 `optional:`，缺配置会直接起不来。
