# 对外契约清单（docs/contracts）

> 这个目录登记全景商城**所有服务的对外契约**。
> 它是**页面契约的唯一裁决点** —— 前端只照这里的表写，不照后端代码写。

## 为什么单独有这么一层

契约是「别人依赖的接口」。此前它们散落在各模块 README 的散文与根 `CLAUDE.md` 的规约段落里，
后果是：**无法枚举、无法核对、只能靠自觉**。单边改动不会编译报错，只会在运行时静默断链
（字段名对不上、权限串改了前端没跟、Redis 键格式单边改了网关验不过）。

本目录把契约从散文变成**可枚举、可机器核对**的清单，并配一个静态检查器兜底。

## 三层契约

| 层 | 含义 | 载体文件 | 消费者 | 公网可达 |
|---|---|---|---|---|
| **① 页面级** | 端 BFF 对外暴露的 HTTP 面，**前端唯一可见的接口** | `admin.md` / `store-bff.md` / `mall-bff.md` | 前端 | ✅ 经网关 |
| **② 内部 Feign** | BFF → 纯域的内部调用，不开放公网路由 | `goods-center.md` / `store.md` / `trade-center.md` | 端 BFF | ❌ 仅内网 |
| **③ 跨服务隐式** | 不属于任何单个服务、却由多方共守的约定 | `cross-cutting.md` | 全部服务 | — |
| **— 基础设施** | 网关路由 / 白名单 / 守卫顺序 | `gateway.md` | 全部流量 | — |

⚠ 三层的**形状规则不同**，不要混：

- 页面级**必包** `RespData{code,msg,data}`，且是 `@PreAuthorize` 授权的唯一位置；
- 内部 Feign **不包** `RespData`，直接返回业务类型，且**不做任何鉴权/权限判断**。

## 文件一览

| 文件 | 覆盖 | 条数 | 层 |
|---|---|---|---|
| [cross-cutting.md](./cross-cutting.md) | 跨服务隐式契约 | 21 条 | ③ |
| [gateway.md](./gateway.md) | 网关路由 / 白名单 / 守卫 | 3 条路由 | 基础设施 |
| [admin.md](./admin.md) | admin 端 BFF 对外接口 | 55 | ① |
| [store-bff.md](./store-bff.md) | 店铺端 BFF 对外接口 | 20 | ① |
| [goods-center.md](./goods-center.md) | 标准商品域内部接口 | 19 | ② |
| [store.md](./store.md) | 店铺域内部接口 | 22 | ② |
| [mall-bff.md](./mall-bff.md) | 商城前台 BFF 对外接口 | 9 | ① |
| [trade-center.md](./trade-center.md) | 交易域 | 待建 | ② |

## 契约表格式（硬约定，检查器依赖）

每个契约文件**开头**必须有一段 HTML 注释形式的元数据块（渲染时不可见），检查器据此决定扫哪些源码：

**页面级**：

```html
<!-- contract-meta
service: admin
layer: page
baseUrl: /admin
scanDirs: backend/admin/src/main/java/com/panoramic/admin/controller
typeDirs: backend/goods-center-interface/src/main/java, backend/store-interface/src/main/java, backend/common/src/main/java, backend/admin/src/main/java
-->
```

- `scanDirs`：扫这个服务的 Controller 源码，用来和表比对端点（逗号分隔多个目录）。
- `typeDirs`：查表里入出参类型名是否存在时搜的目录。页面级要同时给**两个接口模块**（`backend/goods-center-interface/src/main/java`、`backend/store-interface/src/main/java`）、`common`（基座类型）和**本服务自己的** dto/vo 目录（BFF 私有类型不在任何接口模块里）。

**内部 Feign**（两端都要登记，所以既扫 Feign 声明也扫域实现）：

```html
<!-- contract-meta
service: goods-center
layer: internal
basePath: /internal/goods
feignClient: backend/goods-center-interface/src/main/java/com/panoramic/contract/goods/api/GoodsCenterClient.java
implScanDirs: backend/goods-center/src/main/java/com/panoramic/goods/controller
-->
```

**网关**：

```html
<!-- contract-meta
service: gateway
layer: gateway
config: backend/gateway/src/main/resources/application.yml
-->
```

列名固定，**不许增删改**（检查器按列名取值）：

| 层 | 列 |
|---|---|
| 页面级 | `方法` `路径` `权限串` `入参` `出参` `声明位置` `状态` |
| 内部 Feign | `Feign 方法` `方法` `路径` `入参` `出参` `契约声明(接口模块)` `域实现` `调用方` `状态` |

填写口径：

- **路径**：不带网关前缀（`/admin`、`/store` 不算），不带 `@FeignClient` 的 `path` 前缀；路径变量花括号原样保留；**带前导 `/`**。
- **权限串**：`@PreAuthorize` 引号内的字面量；没有则填 `—`。
- **入参 / 出参**：**只要类型名**，不写 `@RequestBody` 之类的注解；泛型原样保留；无参填 `—`。
- **状态**：**留空 = 已实现**；契约先行、代码还没写时才填 `待实现`（见下节）。只认这两个写法 + 留空，
  写别的字（`TODO`、`待实线`）会被检查器判错——打错字若被当成"已实现"，这条契约就静默消失了。
- ⚠ **不要把 DTO 的字段抄进表里**。字段的唯一源是各域接口模块（`<域>-interface`）里的 DTO 类；抄一份就是制造第二个会漂移的地方
  （违反仓库既有的「禁止各自复制一份导致漂移」）。表只登记**有哪些接口、形状是什么、类型在哪、谁在调**。

> 「状态」列只适用于**页面级与内部 Feign** 两张表。网关页（`gateway.md`）的表格由检查器的**网关专项**核对
> （路由 ↔ `application.yml`、白名单互查），文件本身不走通用表解析器，所以不参与待实现机制。

## 契约先行：`待实现` 标记

契约可以先于实现落盘：接口先定下来，前端照表开发，后端再补代码。标记就是让这件事**可核对**的机制。

| 契约行状态 | 代码里有 | 检查器结果 |
|---|---|---|
| 留空 / `已实现` | 有 | ✅ 正常核对（路径、权限串、入出参类型全查） |
| 留空 / `已实现` | 没有 | ❌ **报错**「契约表有、代码没有（幽灵行）」 |
| `待实现` | 没有 | ✅ 放行，计入 `i 待实现 N 条`，不阻塞退出码 |
| **`待实现`** | **有** | ❌ **报错**「代码里已有该接口，请摘掉标记」 |
| 不在表里 | 有 | ❌ **报错**「代码有、契约表没有」 |

第 4 行是这套机制的**反向哨兵**：契约先行最容易烂尾的地方，就是实现完了忘记摘标记 ——
标记一旦烂掉，这张表就开始骗人（读者以为没做，实际做了）。**摘没摘由检查器强制，不靠自觉。**

`待实现` 行**只校验路径 / 方法 / 权限串的写法**（防手滑打错），**不查**入出参类型是否存在、
权限串是否已在 `sys_permission` 种子里 —— 契约先行时这些本来也还没写，查了必然报错，那契约就落不了盘。
待实现行的 `声明位置` / `契约声明(接口模块)` / `域实现` 填 `—`（代码不存在，写计划落点只会变成新的漂移点）。

## 维护规则（三条硬性）

1. **改任何对外接口**（增删改路径 / 方法 / 权限串 / 入出参类型）时，**同一改动内**更新对应 `<服务>.md`。
   **实现完一个 `待实现` 接口后，同一改动内把该行的「状态」摘回留空** —— 不摘，检查器会报错（反向哨兵）。
2. **改跨服务隐式契约**（见 `cross-cutting.md`）时，**必须**同步更新该文件；改动隐式契约而只改代码，视为未完成。
3. **提交前跑一次检查器**，差集非空不得提交：

```bash
node docs/contracts/drift-check.mjs
```

退出码 `0` = 契约与代码一致；非 `0` = 有漂移，按输出逐条修正。

## 与其它文档的边界（文件专项专用）

一个文件只有一个职责，**同一内容不写两遍**：

| 文件 | 只写什么 | 不写什么 |
|---|---|---|
| `backend/<模块>/README.md` | **服务说明**：职责 / 架构位置 / 实体标记 / 边界 | ❌ 接口清单、契约细节（→ 本目录） |
| `docs/contracts/<服务>.md` | **接口契约**：有哪些端点、形状、类型在哪、谁调 | ❌ 业务规则散文（→ 模块 README） |
| `docs/contracts/cross-cutting.md` | **跨服务隐式契约** | ❌ 单个服务的接口 |
| `docs/contracts/README.md`（本文件） | **索引**：三层含义 / 格式 / 维护规则 | ❌ 契约条目本身 |
| `docs/contracts/drift-check.mjs` | **检查器** | — |
| `<模块>/src/main/resources/db/*.sql` | **表结构与种子数据** | ❌ 接口清单 |
