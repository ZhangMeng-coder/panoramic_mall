<!-- contract-meta
service: trade-center
layer: internal
basePath: /internal/trade
feignClient: backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/api/TradeCenterClient.java
implScanDirs: backend/trade-center/src/main/java/com/panoramic/trade/controller
typeDirs: backend/trade-center-interface/src/main/java
-->

# 交易域（trade-center）内部契约 · 第 ② 层

> 交易域：**购物车 `trade_cart_item`**。
> **不暴露公网路由**，只被 mall-bff 经内部 Feign 调用。
> 域内**不做任何鉴权、不做权限判断**（见 [cross-cutting.md](./cross-cutting.md) 第 6、7、14 条）。

**共 8 个接口**（全部为 C 端顾客自助购物车，无 owner / platform 分侧）。

## 一、归属与形状

- **锚点 `customerId` = `mall_user.id`**：跨域 id 引用、**无外键**，与 `store_id` = 店主账号 id 同一手法。
  所有方法**全按传入锚点过滤**，`customerId` 就是数据权限本身。
- **无 owner / platform 分侧**：本期只做 **C 端自助**，没有平台侧能力，故不存在 store 那种
  「owner / platform 分流由 BFF 调哪一侧决定」的形态（对比 [store.md](./store.md) 第三节）。
- **域内不做任何身份判断**：不判 `X-User-Type`、不校验 token、无 `@PreAuthorize`；
  身份头只读来填 `UserContext`，且**仅用于审计留痕**。
  调用方传的 `customerId` 是否真是「本人」，**由 mall-bff 从登录态取**，域侧不校验 —— 防线在 BFF。
- **形状**：**不包 `RespData`**，错误走 `{code,msg}` + 真实 HTTP 状态 —— 见 [cross-cutting.md](./cross-cutting.md) 第 2 条。
- **前缀**：`/internal/trade`；拼法（不是 context-path、由 Controller 类级 `@RequestMapping` 写死）
  见 [README.md](./README.md) 的「内部 Feign 的「路径」前缀怎么来的」。
- ⚠ **本域是全仓库唯一加载 `datasource-redis.yml` 的域服务**（购物车的加购去重与计数缓存），
  是 [cross-cutting.md](./cross-cutting.md) 第 12 条「Nacos 加载矩阵」的**登记例外**。
  ⚠ 它**只把 Redis 当缓存/提示**，不改变「域内不鉴权」：Redis 里没有登录态，MySQL 始终是唯一事实源；
  两个方向的错判（误报/漏报）都由写路径自愈，见 [`backend/trade-center/README.md`](../../backend/trade-center/README.md)。
- ⚠ **购物车行是「C 端商品可见性」不变量（[cross-cutting.md](./cross-cutting.md) 第 20 条）的第三个落点**：
  可见性判定（店铺已审核 + SPU 已上架 + 未锁定）**不在域内做**，域只按 `spuId` / `skuId` 出原始行；
  「这一行还算不算可买」由 mall-bff 读时判定并打 `invalid` 标记。

## 二、接口清单

| Feign 方法 | 方法 | 路径 | 入参 | 出参 | 契约声明(接口模块) | 域实现 | 调用方 | 状态 |
|---|---|---|---|---|---|---|---|---|
| listCartItems | GET | /cart/{customerId} | `Long` | `List<TradeCartItemVO>` | TradeCenterClient.java:52 | CartController.java:44 | CartBffService(mall-bff) |  |
| cartItemCount | GET | /cart/{customerId}/count | `Long` | `Integer` | TradeCenterClient.java:61 | CartController.java:52 | CartBffService(mall-bff) |  |
| addCartItem | POST | /cart/{customerId}/items | `Long`, `TradeCartItemAddDTO` | `Long` | TradeCenterClient.java:72 | CartController.java:60 | CartBffService(mall-bff) |  |
| updateCartItemQuantity | PUT | /cart/{customerId}/items/{id} | `Long`, `Long`, `TradeCartItemUpdateDTO` | `void` | TradeCenterClient.java:83 | CartController.java:69 | CartBffService(mall-bff) |  |
| setCartItemSelected | PUT | /cart/{customerId}/items/{id}/selected | `Long`, `Long`, `TradeCartSelectDTO` | `void` | TradeCenterClient.java:95 | CartController.java:79 | CartBffService(mall-bff) |  |
| setAllCartItemsSelected | PUT | /cart/{customerId}/selected | `Long`, `TradeCartSelectDTO` | `void` | TradeCenterClient.java:108 | CartController.java:89 | CartBffService(mall-bff) |  |
| removeCartItems | POST | /cart/{customerId}/items/remove | `Long`, `TradeCartItemIdsDTO` | `void` | TradeCenterClient.java:118 | CartController.java:98 | CartBffService(mall-bff) |  |
| clearCart | DELETE | /cart/{customerId} | `Long` | `void` | TradeCenterClient.java:127 | CartController.java:107 | CartBffService(mall-bff) |  |

> 「入参」列里**多个 `Long` 同时出现**时，第一个是 **`customerId`**（数据权限锚点），第二个是行 `id`。
> 例：`updateCartItemQuantity` 的 `Long, Long, DTO` = `customerId, id, dto`。

> 字段定义**不在本表**，去 `trade-center-interface`（包根 `com.panoramic.contract.trade`）的
> `dto` / `vo` 包里看；表里只登记**有哪些接口、形状是什么、类型在哪、谁在调**，不抄字段。

> 删除接口用 **`POST .../items/remove` + `@RequestBody`**（而非 `DELETE` 带 body，或逐个 `DELETE`）：
> 批量删除口径与跨店通用侧同形，见 [cross-cutting.md](./cross-cutting.md) 第 18 条。

## 三、类型所在包（全部在 `trade-center-interface`，两端引用同一份）

包根：`backend/trade-center-interface/src/main/java/com/panoramic/contract/trade/`

| 包 | 类型 |
|---|---|
| `dto` | TradeCartItemAddDTO, TradeCartItemIdsDTO, TradeCartItemUpdateDTO, TradeCartSelectDTO |
| `vo` | TradeCartItemVO |

## 四、业务规则去哪看

购物车行的落库口径（`(customer_id, sku_id)` 唯一键 + **物理删除**）、数量与行数上限（单行 ≤ 999、单购物车 ≤ 100 行）、
加购的「查重 → 自增」双向回退、选中状态持久化、Redis 两处失效的可自愈性、
以及**订单领域模型**的完整口径（状态机 / 配置驱动的生成流水线 / 一单一店拆单 / 两级幂等 / 回滚与保存时机）——
见 [`backend/trade-center/README.md`](../../backend/trade-center/README.md)「三、职责与边界」第 7 节。
⚠ 订单模型**本期无接口、不落库**（只由单测验证），故**在下面的接口表里没有任何一行**——这不是漏登记。
本文件只写接口与形状。
