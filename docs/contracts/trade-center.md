<!-- contract-meta
service: trade-center
layer: internal
basePath: /internal/trade
feignClient: backend/common/src/main/java/com/panoramic/common/trade/api/TradeCenterClient.java
implScanDirs: backend/trade-center/src/main/java/com/panoramic/trade/controller
typeDirs: backend/common/src/main/java
-->

# 交易域（trade-center）· 第 ② 层 —— **待建**

> ⚠ **本服务尚不存在。** 此文件为占位，用于固定它在契约体系中的位置与既定约定。
> 待建项记录在仓库根 `todo.md`。

## 一、归属（术语防呆）

根 `CLAUDE.md` 已划定：**"顾客 / 购物车 / 订单 / 评价"属未来 trade 域**。
⚠ 别把这些实体塞进 `goods-center`（标准商品模板库）或 `store`（店铺在售商品/库存/信誉）。

## 二、已确定、不可改的部分

| 项 | 约定 | 依据 |
|---|---|---|
| 层 | 第 ② 层（纯域）：**不暴露公网路由**，只被端 BFF 经内部 Feign 调用 | [README.md](./README.md) 三层定义 |
| 鉴权 | **不做任何鉴权、不做权限判断、不校验 token**；只读身份头填 `UserContext`，且**仅用于审计留痕** | [cross-cutting.md](./cross-cutting.md) 第 6、7 条 |
| 依赖 | 只依赖 `common`（**不依赖 `common-auth`**）→ 结构上拿不到认证链与 Redis | `backend/README.md` 模块约定 |
| 形状 | **不包 `RespData`**，直接返回业务结果类型；错误抛 `ServiceException` + 真实 HTTP 状态 | [cross-cutting.md](./cross-cutting.md) 第 2、12 条 |
| 类型 | 入出参 DTO 放 `common`，调用方与被调方引用**同一份** | 同上第 13 条 |
| 熔断 | 端 BFF 侧配 `ignore-exceptions: ServiceException`（4xx 不计失败率） | 同上第 12 条 |
| Nacos | 需加载 `datasource-mysql.yml`；**不加载** `datasource-redis` / `auth` / `feign-circuitbreaker` | 同上第 11 条加载矩阵 |
| 路由前缀 | 类级 `@RequestMapping` 写死 `/internal/trade/xxx`（本仓库既有做法，**不用 context-path**） | 见 [goods-center.md](./goods-center.md) 第一节 |

## 三、接口清单

**待建** —— 服务创建后按 [README.md](./README.md) 的格式补齐本表，并同步更新索引里的条数。
